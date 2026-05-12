package es.com.adakadavra.agent.jarvis.copilot;

import es.com.adakadavra.agent.jarvis.model.CopilotRequest;
import es.com.adakadavra.agent.jarvis.model.CopilotResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Invoca el GitHub Copilot CLI ({@code gh copilot suggest / explain}) como subproceso.
 *
 * <p>Requisitos:
 * <ul>
 *   <li>GitHub CLI ({@code gh}) instalado y la extensión copilot: {@code gh extension install github/gh-copilot}</li>
 *   <li>Token de GitHub en {@code GH_TOKEN} o en la sesión activa de {@code gh auth login}</li>
 *   <li>Ruta al binario configurable con {@code jarvis.copilot.cli.path}</li>
 * </ul>
 */
@Service
public class CopilotCliService {

    private static final Logger log = LoggerFactory.getLogger(CopilotCliService.class);
    private static final int TIMEOUT_SECONDS = 60;

    // Elimina secuencias de escape ANSI (colores, movimientos de cursor, etc.)
    private static final Pattern ANSI = Pattern.compile(
            "\\[[;\\d]*[mA-Za-z]|\\[\\?[\\d]+[lh]|\r");

    // Líneas de la TUI interactiva que no son contenido útil
    private static final List<String> TUI_PREFIXES = List.of(
            "Welcome to GitHub Copilot", "version ", "? What ", "? Select",
            "Use arrows to move", "Copy command to clipboard", "Explain command",
            "Execute command", "Revise command", "Rate response", "Exit",
            "❯", "> ");

    @Value("${jarvis.copilot.cli.path:/usr/local/bin/gh}")
    private String ghPath;

    @Value("${jarvis.copilot.github-token:}")
    private String githubToken;

    // ── Chat (síncrono) ───────────────────────────────────────────────────────

    public Mono<CopilotResponse> chat(CopilotRequest request) {
        return Mono.fromCallable(() -> doChat(request))
                   .subscribeOn(Schedulers.boundedElastic());
    }

    private CopilotResponse doChat(CopilotRequest request) throws IOException, InterruptedException {
        List<String> cmd = buildCommand(request);
        log.debug("Copilot CLI command: {}", cmd);

        Process process = startProcess(cmd);

        // Escribe "q\n" al stdin en un hilo separado para salir del menú interactivo
        // sin bloquear la lectura de stdout
        Thread exitThread = new Thread(() -> {
            try {
                Thread.sleep(200);
                process.getOutputStream().write(("q\n").getBytes());
                process.getOutputStream().flush();
                process.getOutputStream().close();
            } catch (Exception ignored) {}
        });
        exitThread.setDaemon(true);
        exitThread.start();

        String rawOutput = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("Copilot CLI timed out after {}s", TIMEOUT_SECONDS);
        }

        String parsed = parseOutput(rawOutput, request.mode());
        return new CopilotResponse(parsed, request.mode());
    }

    // ── Stream (SSE) ──────────────────────────────────────────────────────────

    public Flux<String> stream(CopilotRequest request) {
        return Flux.<String>create(sink -> {
            try {
                List<String> cmd = buildCommand(request);
                log.debug("Copilot CLI stream command: {}", cmd);

                Process process = startProcess(cmd);

                // Pre-escribe "q\n" para que el proceso salga del menú interactivo
                Thread exitThread = new Thread(() -> {
                    try {
                        Thread.sleep(300);
                        process.getOutputStream().write("q\n".getBytes());
                        process.getOutputStream().flush();
                        process.getOutputStream().close();
                    } catch (Exception ignored) {}
                });
                exitThread.setDaemon(true);
                exitThread.start();

                boolean inContent = false;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        String clean = ANSI.matcher(line).replaceAll("").trim();

                        // Activar emisión cuando encontramos el marcador de contenido
                        if (clean.equals("Suggestion:") || clean.equals("Explanation:")) {
                            inContent = true;
                            continue;
                        }

                        // Detener cuando aparece el menú de opciones
                        if (isTuiLine(clean)) {
                            inContent = false;
                            continue;
                        }

                        if (inContent && !clean.isBlank()) {
                            sink.next(clean + "\n");
                        }
                    }
                }

                process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (process.isAlive()) process.destroyForcibly();

                sink.next("[DONE] {\"type\":\"done\"}");
                sink.complete();

            } catch (Exception e) {
                log.error("Copilot CLI stream error", e);
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> buildCommand(CopilotRequest request) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ghPath);
        cmd.add("copilot");

        if ("explain".equals(request.mode())) {
            cmd.add("explain");
        } else {
            cmd.add("suggest");
            cmd.add("-t");
            cmd.add(resolveTarget(request.target()));
        }

        cmd.add(request.message());
        return cmd;
    }

    private Process startProcess(List<String> cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        if (!githubToken.isBlank()) {
            pb.environment().put("GH_TOKEN", githubToken);
            pb.environment().put("GITHUB_TOKEN", githubToken);
        }
        return pb.start();
    }

    private String parseOutput(String raw, String mode) {
        String stripped = ANSI.matcher(raw).replaceAll("");
        String marker = "explain".equals(mode) ? "Explanation:" : "Suggestion:";

        int markerIdx = stripped.indexOf(marker);
        if (markerIdx < 0) {
            // Sin marcador: devolver líneas que no sean de TUI
            return stripped.lines()
                    .map(String::trim)
                    .filter(l -> !l.isBlank() && !isTuiLine(l))
                    .reduce("", (a, b) -> a.isBlank() ? b : a + "\n" + b)
                    .strip();
        }

        String after = stripped.substring(markerIdx + marker.length());

        // Cortar en el primer indicador de menú interactivo
        for (String tuiMarker : List.of("? Select an option", "? What ")) {
            int tuiIdx = after.indexOf(tuiMarker);
            if (tuiIdx >= 0) {
                after = after.substring(0, tuiIdx);
                break;
            }
        }

        return after.strip();
    }

    private boolean isTuiLine(String line) {
        if (line.isBlank()) return false;
        for (String prefix : TUI_PREFIXES) {
            if (line.startsWith(prefix) || line.contains(prefix)) return true;
        }
        return false;
    }

    private String resolveTarget(String target) {
        if (target == null || target.isBlank()) return "shell";
        return switch (target.toLowerCase()) {
            case "git" -> "git";
            case "gh"  -> "gh";
            default    -> "shell";
        };
    }
}
