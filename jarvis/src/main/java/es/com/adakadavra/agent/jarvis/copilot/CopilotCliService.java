package es.com.adakadavra.agent.jarvis.copilot;

import es.com.adakadavra.agent.jarvis.model.CopilotRequest;
import es.com.adakadavra.agent.jarvis.model.CopilotResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class CopilotCliService {

    private static final Logger logger = LoggerFactory.getLogger(CopilotCliService.class);

    private static final Pattern ANSI_PATTERN = Pattern.compile(
            "\\[[;\\d]*[mA-Za-z]|\\[\\?[\\d]+[lh]|\r");

    private static final Pattern TUI_LINE = Pattern.compile(
            "^[\\s>?✓✗●○◉◎│─╭╰╰╯┌└┐┘├┤┬┴╔╚╗╝╠╣╦╩].*|" +
            ".*\\u001b.*|" +
            "^\\s*$");

    @Value("${jarvis.copilot.cli.path:/usr/local/bin/gh}")
    private String ghPath;

    @Value("${jarvis.copilot.github-token:}")
    private String githubToken;

    public CopilotResponse execute(CopilotRequest request) {
        List<String> cmd = buildCommand(request);
        logger.info("[COPILOT] Running: {}", String.join(" ", cmd));

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            if (githubToken != null && !githubToken.isBlank()) {
                pb.environment().put("GH_TOKEN", githubToken);
                pb.environment().put("GITHUB_TOKEN", githubToken);
            }

            Process process = pb.start();

            Thread qThread = new Thread(() -> {
                try {
                    Thread.sleep(200);
                    process.outputStream().write("q\n".getBytes(StandardCharsets.UTF_8));
                    process.outputStream().flush();
                } catch (Exception ignored) {}
            });
            qThread.setDaemon(true);
            qThread.start();

            String rawOutput;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.inputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                rawOutput = sb.toString();
            }

            process.waitFor();
            String cleaned = parseOutput(rawOutput, request.mode());
            return new CopilotResponse(cleaned, request.mode());

        } catch (Exception e) {
            logger.error("[COPILOT] Error executing gh copilot: {}", e.getMessage(), e);
            return new CopilotResponse("Error al ejecutar gh copilot: " + e.getMessage(), request.mode());
        }
    }

    public Flux<String> stream(CopilotRequest request) {
        List<String> cmd = buildCommand(request);
        logger.info("[COPILOT STREAM] Running: {}", String.join(" ", cmd));

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        Thread thread = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                if (githubToken != null && !githubToken.isBlank()) {
                    pb.environment().put("GH_TOKEN", githubToken);
                    pb.environment().put("GITHUB_TOKEN", githubToken);
                }

                Process process = pb.start();

                Thread qThread = new Thread(() -> {
                    try {
                        Thread.sleep(200);
                        process.outputStream().write("q\n".getBytes(StandardCharsets.UTF_8));
                        process.outputStream().flush();
                    } catch (Exception ignored) {}
                });
                qThread.setDaemon(true);
                qThread.start();

                boolean inContent = false;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.inputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String clean = stripAnsi(line);
                        if (isTuiLine(clean)) continue;

                        if (!inContent) {
                            if (clean.contains("Suggestion:") || clean.contains("Explanation:")) {
                                inContent = true;
                                String after = clean.replaceFirst("(Suggestion:|Explanation:)", "").trim();
                                if (!after.isBlank()) {
                                    sink.tryEmitNext(after + "\n");
                                }
                            }
                        } else {
                            if (clean.contains("? Select an option") || clean.startsWith("? ")) {
                                break;
                            }
                            sink.tryEmitNext(clean + "\n");
                        }
                    }
                }

                process.waitFor();
                sink.tryEmitNext("[DONE] {\"type\":\"done\"}");
                sink.tryEmitComplete();

            } catch (Exception e) {
                logger.error("[COPILOT STREAM] Error: {}", e.getMessage(), e);
                sink.tryEmitError(e);
            }
        });
        thread.setDaemon(true);
        thread.start();

        return sink.asFlux();
    }

    private List<String> buildCommand(CopilotRequest request) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ghPath);
        cmd.add("copilot");

        String mode = request.mode() != null ? request.mode() : "suggest";
        if ("explain".equals(mode)) {
            cmd.add("explain");
            cmd.add(request.message());
        } else {
            cmd.add("suggest");
            String target = request.target() != null ? request.target() : "shell";
            cmd.add("-t");
            cmd.add(target);
            cmd.add(request.message());
        }
        return cmd;
    }

    private String parseOutput(String raw, String mode) {
        String[] lines = raw.split("\n");
        StringBuilder result = new StringBuilder();
        boolean inContent = false;

        for (String line : lines) {
            String clean = stripAnsi(line);
            if (isTuiLine(clean)) continue;

            if (!inContent) {
                if (clean.contains("Suggestion:") || clean.contains("Explanation:")) {
                    inContent = true;
                    String after = clean.replaceFirst("(Suggestion:|Explanation:)", "").trim();
                    if (!after.isBlank()) result.append(after).append("\n");
                }
            } else {
                if (clean.contains("? Select an option") || clean.startsWith("? ")) break;
                result.append(clean).append("\n");
            }
        }

        String output = result.toString().strip();
        return output.isBlank() ? "(gh copilot no devolvió sugerencia)" : output;
    }

    private String stripAnsi(String text) {
        return ANSI_PATTERN.matcher(text).replaceAll("");
    }

    private boolean isTuiLine(String line) {
        if (line.isBlank()) return true;
        // Filter obvious TUI/control characters
        return line.contains("") || line.matches("^[\\s╭╰│─╯╮✓✗●○◉◎►]+.*");
    }
}
