package es.com.adakadavra.agent.jarvis.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.com.adakadavra.agent.jarvis.model.CliRequest;
import es.com.adakadavra.agent.jarvis.model.CliResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Llama al Claude CLI (claude -p) como subproceso para responder peticiones.
 * Soporta modo síncrono (JSON) y modo streaming (stream-json / SSE).
 *
 * <p>Requisitos:
 * <ul>
 *   <li>El binario {@code claude} accesible en {@code jarvis.claude.cli.path}.</li>
 *   <li>La variable {@code ANTHROPIC_API_KEY} disponible en el entorno del servidor
 *       o configurada en {@code spring.ai.anthropic.api-key}.</li>
 * </ul>
 */
@Service
public class ClaudeCliService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliService.class);
    private static final int PROCESS_TIMEOUT_SECONDS = 120;

    @Value("${jarvis.claude.cli.path:/opt/node22/bin/claude}")
    private String cliPath;

    @Value("${spring.ai.anthropic.api-key:}")
    private String anthropicApiKey;

    private final ObjectMapper mapper = new ObjectMapper();

    // ── Chat (síncrono) ───────────────────────────────────────────────────────

    /**
     * Llama al CLI en modo no interactivo con salida JSON.
     * Se ejecuta en un hilo elástico para no bloquear el event loop.
     */
    public Mono<CliResponse> chat(CliRequest request) {
        return Mono.fromCallable(() -> doChat(request))
                   .subscribeOn(Schedulers.boundedElastic());
    }

    private CliResponse doChat(CliRequest request) throws IOException, InterruptedException {
        List<String> cmd = buildCommand(request, false);
        log.debug("Claude CLI chat command: {}", cmd);

        Process process = startProcess(cmd);
        writeStdin(process, request.message());

        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());
        boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Claude CLI timed out after " + PROCESS_TIMEOUT_SECONDS + "s");
        }
        if (process.exitValue() != 0) {
            log.warn("Claude CLI exited with code {}: {}", process.exitValue(), stderr);
        }

        return parseJsonResponse(stdout);
    }

    // ── Stream (SSE) ──────────────────────────────────────────────────────────

    /**
     * Llama al CLI con {@code --output-format=stream-json} y emite tokens de texto
     * conforme los produce el modelo. Termina el Flux con un evento JSON especial
     * {@code {"type":"done","sessionId":"...","costUsd":0.0}} para que el cliente
     * pueda recuperar el ID de sesión.
     */
    public Flux<String> stream(CliRequest request) {
        return Flux.<String>create(sink -> {
            try {
                List<String> cmd = buildCommand(request, true);
                log.debug("Claude CLI stream command: {}", cmd);

                Process process = startProcess(cmd);
                writeStdin(process, request.message());

                int lastTextLength = 0;
                String sessionId = null;
                double costUsd = 0.0;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;
                        try {
                            JsonNode event = mapper.readTree(line);
                            String type = event.path("type").asText("");

                            switch (type) {
                                case "assistant" -> {
                                    // El CLI emite el texto acumulado; calculamos el delta
                                    String text = extractAssistantText(event);
                                    if (text.length() > lastTextLength) {
                                        sink.next(text.substring(lastTextLength));
                                        lastTextLength = text.length();
                                    }
                                }
                                case "result" -> {
                                    sessionId = event.path("session_id").asText(null);
                                    costUsd   = event.path("total_cost_usd").asDouble(0.0);
                                    // Si el CLI no envió partial messages, emitimos el resultado completo
                                    String result = event.path("result").asText("");
                                    if (lastTextLength == 0 && !result.isBlank()) {
                                        sink.next(result);
                                    }
                                }
                                default -> log.trace("CLI event type={}: {}", type, line);
                            }
                        } catch (Exception e) {
                            log.debug("Could not parse CLI stream line: {}", line);
                        }
                    }
                }

                process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                // Emitir evento de finalización con metadata
                String doneEvent = mapper.writeValueAsString(
                        new DoneEvent("done", sessionId, costUsd));
                sink.next("[DONE] " + doneEvent);
                sink.complete();

            } catch (Exception e) {
                log.error("Claude CLI stream error", e);
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> buildCommand(CliRequest request, boolean streaming) {
        List<String> cmd = new ArrayList<>();
        cmd.add(cliPath);
        cmd.add("--print");
        cmd.add("--bare");
        cmd.add("--output-format=" + (streaming ? "stream-json" : "json"));
        cmd.add("--model");
        cmd.add(resolveModel(request.model()));

        if (request.sessionId() != null && !request.sessionId().isBlank()) {
            cmd.add("--resume");
            cmd.add(request.sessionId());
        } else {
            cmd.add("--no-session-persistence");
        }

        if (streaming) {
            cmd.add("--include-partial-messages");
        }

        return cmd;
    }

    private Process startProcess(List<String> cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        if (!anthropicApiKey.isBlank()) {
            pb.environment().put("ANTHROPIC_API_KEY", anthropicApiKey);
        }
        return pb.start();
    }

    private void writeStdin(Process process, String message) throws IOException {
        try (PrintWriter writer = new PrintWriter(process.getOutputStream())) {
            writer.println(message);
        }
    }

    private CliResponse parseJsonResponse(String json) throws IOException {
        if (json.isBlank()) {
            throw new RuntimeException("Claude CLI returned empty output");
        }
        JsonNode root = mapper.readTree(json);
        if (root.path("is_error").asBoolean(false)) {
            throw new RuntimeException("Claude CLI error: " + root.path("result").asText());
        }
        return new CliResponse(
                root.path("result").asText(""),
                root.path("session_id").asText(null),
                root.path("total_cost_usd").asDouble(0.0));
    }

    private String extractAssistantText(JsonNode assistantEvent) {
        return assistantEvent.path("message")
                             .path("content")
                             .path(0)
                             .path("text")
                             .asText("");
    }

    private String resolveModel(String requested) {
        if (requested == null || requested.isBlank()) return "sonnet";
        return switch (requested.toLowerCase()) {
            case "opus"   -> "opus";
            case "haiku"  -> "haiku";
            case "sonnet" -> "sonnet";
            default       -> requested; // nombre completo pasado tal cual
        };
    }

    // ── Evento de finalización ────────────────────────────────────────────────

    record DoneEvent(String type, String sessionId, double costUsd) {}
}
