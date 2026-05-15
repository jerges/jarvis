package es.com.adakadavra.agent.jarvis.cli.claude;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ClaudeCliClient {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeCliClient.class);

    private final es.com.adakadavra.agent.jarvis.cli.claude.ClaudeCliJsonParser parser;
    private final String command;
    private final Path workingDirectory;
    private final Duration timeout;

    private static final String DEFAULT_MODEL = "haiku";
    private static final String ALLOWED_MODELS_HINT = "Allowed Claude CLI models: haiku, sonnet, opus";

    public ClaudeCliClient(es.com.adakadavra.agent.jarvis.cli.claude.ClaudeCliJsonParser parser, String command, Path workingDirectory, Duration timeout) {
        this.parser = parser;
        this.command = command;
        this.workingDirectory = workingDirectory;
        this.timeout = timeout;
    }

    public es.com.adakadavra.agent.jarvis.cli.claude.ClaudeCliResponse execute(String systemPrompt, String userPrompt, String model) {
        List<String> commandLine = buildPrintCommand(systemPrompt, userPrompt, model);
        String output = executeCommand(commandLine);
        return parser.parseResponse(output, model);
    }

    public es.com.adakadavra.agent.jarvis.model.RoutingDecision route(String systemPrompt, String userPrompt, String model) {
        List<String> commandLine = buildRoutingCommand(systemPrompt, userPrompt, model);
        String output = executeCommand(commandLine);
        return parser.parseRoutingDecision(output);
    }

    public Flux<String> stream(String systemPrompt, String userPrompt, String model) {
        List<String> commandLine = buildStreamCommand(systemPrompt, userPrompt, model);
        return Flux.create(sink -> {
            Process process;
            try {
                process = startProcess(commandLine);
            } catch (IOException ex) {
                sink.error(new IllegalStateException("Could not start Claude CLI stream process", ex));
                return;
            }

            sink.onDispose(() -> {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            });

            CompletableFuture.runAsync(() -> {
                StringBuilder diagnosticOutput = new StringBuilder();
                try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        parser.extractStreamingText(line).ifPresent(sink::next);
                        diagnosticOutput.append(line).append(System.lineSeparator());
                    }

                    if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        sink.error(new IllegalStateException("Claude CLI stream command timed out"));
                        return;
                    }

                    if (process.exitValue() != 0) {
                        sink.error(new IllegalStateException("Claude CLI stream command failed: " + diagnosticOutput));
                        return;
                    }

                    sink.complete();
                } catch (IOException ex) {
                    sink.error(new IllegalStateException("Could not read Claude CLI stream output", ex));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    sink.error(new IllegalStateException("Claude CLI stream command was interrupted", ex));
                }
            });
        });
    }

    private String executeCommand(List<String> commandLine) {
        try {
            Process process = startProcess(commandLine);
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readStdout(process));

            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Claude CLI command timed out");
            }

            String output = outputFuture.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Claude CLI command failed: " + output);
            }
            return output;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start Claude CLI process", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Claude CLI command was interrupted", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Could not read Claude CLI output", ex);
        }
    }

    private Process startProcess(List<String> commandLine) throws IOException {
        logger.info("Starting Claude CLI command: {}", commandLine);
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true);
        return processBuilder.start();
    }

    private String readStdout(Process process) {
        try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
            return reader.lines().reduce((left, right) -> left + System.lineSeparator() + right).orElse("");
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read Claude CLI stdout", ex);
        }
    }

    private List<String> buildPrintCommand(String systemPrompt, String userPrompt, String model) {
        List<String> commandLine = baseCommand(model);
        commandLine.add("--output-format");
        commandLine.add("json");
        commandLine.add("--system-prompt");
        commandLine.add(systemPrompt);
        commandLine.add(userPrompt);
        return commandLine;
    }

    private List<String> buildRoutingCommand(String systemPrompt, String userPrompt, String model) {
        List<String> commandLine = buildPrintCommand(systemPrompt, userPrompt, model);
        commandLine.add(2, "--json-schema");
        commandLine.add(3, routingSchema());
        return commandLine;
    }

    private List<String> buildStreamCommand(String systemPrompt, String userPrompt, String model) {
        List<String> commandLine = baseCommand(model);
        commandLine.add("--verbose");
        commandLine.add("--output-format");
        commandLine.add("stream-json");
        commandLine.add("--include-partial-messages");
        commandLine.add("--system-prompt");
        commandLine.add(systemPrompt);
        commandLine.add(userPrompt);
        return commandLine;
    }

    private List<String> baseCommand(String model) {
        String modelForCli = resolveModelForCli(model);
        List<String> commandLine = new ArrayList<>();
        commandLine.add(command);
        commandLine.add("-p");
        commandLine.add("--model");
        commandLine.add(modelForCli);
        commandLine.add("--tools");
        commandLine.add("");
        return commandLine;
    }

    String resolveModelForCli(String model) {
        if (model == null || model.isBlank()) {
            return DEFAULT_MODEL;
        }

        String normalized = model.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "haiku", "sonnet", "opus" -> normalized;
            default -> {
                // Accept explicit Claude model ids too, but block non-Claude values (e.g. Ollama models).
                if (normalized.startsWith("claude-haiku-")
                        || normalized.startsWith("claude-sonnet-")
                        || normalized.startsWith("claude-opus-")) {
                    yield normalized;
                }
                throw new IllegalArgumentException("Unsupported Claude CLI model '" + model + "'. " + ALLOWED_MODELS_HINT);
            }
        };
    }

    private String routingSchema() {
        return """
                {
                  \"type\": \"object\",
                  \"properties\": {
                    \"agentType\": {
                      \"type\": \"string\",
                      \"enum\": [\"JARVIS\", \"SECRETARY\", \"SECURITY\", \"SOCIAL_MEDIA\", \"DEVELOPER\", \"DEVOPS\", \"FRONTEND\"]
                    },
                    \"reasoning\": {
                      \"type\": \"string\"
                    }
                  },
                  \"required\": [\"agentType\", \"reasoning\"],
                  \"additionalProperties\": false
                }
                """;
    }
}


