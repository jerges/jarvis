package es.com.adakadavra.agent.jarvis.cli.copilot;

import es.com.adakadavra.agent.jarvis.model.CliProviderStatus;
import es.com.adakadavra.agent.jarvis.model.CopilotRequest;
import es.com.adakadavra.agent.jarvis.model.CopilotResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CopilotCliService {

    private static final Logger logger = LoggerFactory.getLogger(CopilotCliService.class);
    private static final String PROVIDER = "copilot";

    private static final Pattern ANSI_PATTERN = Pattern.compile("\\u001B\\[[;\\d]*[ -/]*[@-~]");

    @Value("${jarvis.copilot.cli.command:gh}")
    private String ghCommand;

    @Value("${jarvis.copilot.github-token:}")
    private String githubToken;

    @Value("${jarvis.copilot.timeout-seconds:90}")
    private long timeoutSeconds;

    @Value("${jarvis.copilot.auth-cache-seconds:60}")
    private long authCacheSeconds;

    private final AtomicReference<AuthStatusCache> authStatusCache = new AtomicReference<>();
    private final AtomicReference<ModelListCache> modelListCache = new AtomicReference<>();

    public CopilotResponse executeForAgent(String systemPrompt, String userPrompt, String model) {
        String mergedPrompt = systemPrompt + "\n\nUser request:\n" + userPrompt;
        return execute(new CopilotRequest(mergedPrompt, "explain", null, PROVIDER, model));
    }

    public Flux<String> streamForAgent(String systemPrompt, String userPrompt, String model) {
        String mergedPrompt = systemPrompt + "\n\nUser request:\n" + userPrompt;
        return stream(new CopilotRequest(mergedPrompt, "explain", null, PROVIDER, model));
    }

    public List<String> listModels() {
        long now = System.currentTimeMillis();
        ModelListCache cached = modelListCache.get();
        if (cached != null && now < cached.expiresAtMillis()) {
            return cached.models();
        }

        List<String> models = loadModels();
        modelListCache.set(new ModelListCache(models, now + (Math.max(0, authCacheSeconds) * 1000)));
        return models;
    }

    public CliProviderStatus providerStatus() {
        CliProviderStatus cached = getCachedProviderStatus();
        if (cached != null) {
            return cached;
        }

        CommandResult auth = runCommand(List.of(ghCommand, "auth", "status"));
        if (auth.exitCode != 0) {
            String output = auth.output.toLowerCase(Locale.ROOT);
            if (output.contains("not logged") || output.contains("login") || output.contains("authentication")) {
                CliProviderStatus status = new CliProviderStatus(PROVIDER, true, false, summarize(auth.output, "GitHub CLI is not authenticated"));
                cacheProviderStatus(status);
                return status;
            }
            CliProviderStatus status = new CliProviderStatus(PROVIDER, false, false, summarize(auth.output, "GitHub CLI is not available"));
            cacheProviderStatus(status);
            return status;
        }

        CommandResult copilotCheck = runCommand(List.of(ghCommand, "copilot", "--help"));
        if (copilotCheck.exitCode != 0) {
            CliProviderStatus status = new CliProviderStatus(PROVIDER, false, false, summarize(copilotCheck.output, "GitHub Copilot CLI is not available"));
            cacheProviderStatus(status);
            return status;
        }

        CliProviderStatus status = new CliProviderStatus(PROVIDER, true, true, "GitHub Copilot CLI authenticated");
        cacheProviderStatus(status);
        return status;
    }

    private CliProviderStatus getCachedProviderStatus() {
        long now = System.currentTimeMillis();
        AuthStatusCache cache = authStatusCache.get();
        if (cache != null && now < cache.expiresAtMillis()) {
            return cache.status();
        }
        return null;
    }

    private synchronized void cacheProviderStatus(CliProviderStatus status) {
        long ttlSeconds = Math.max(0, authCacheSeconds);
        long expiresAtMillis = System.currentTimeMillis() + (ttlSeconds * 1000);
        authStatusCache.set(new AuthStatusCache(status, expiresAtMillis));
    }

    public CopilotResponse execute(CopilotRequest request) {
        String validationError = validateRequest(request);
        if (validationError != null) {
            return new CopilotResponse(validationError, resolveMode(request));
        }

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
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readStdout(process));

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new CopilotResponse("Error ejecutando gh copilot: command timed out", resolveMode(request));
            }

            String rawOutput = outputFuture.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                String cleanedFailure = parseOutput(rawOutput);
                return new CopilotResponse("Error ejecutando gh copilot: " + cleanedFailure, resolveMode(request));
            }

            String cleaned = parseOutput(rawOutput);
            return new CopilotResponse(cleaned, resolveMode(request));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("[COPILOT] Interrupted executing gh copilot", e);
            return new CopilotResponse("Error al ejecutar gh copilot: command interrupted", resolveMode(request));
        } catch (ExecutionException | TimeoutException e) {
            logger.error("[COPILOT] Could not read gh copilot output", e);
            return new CopilotResponse("Error al ejecutar gh copilot: could not read output", resolveMode(request));
        } catch (Exception e) {
            logger.error("[COPILOT] Error executing gh copilot: {}", e.getMessage(), e);
            return new CopilotResponse("Error al ejecutar gh copilot: " + e.getMessage(), resolveMode(request));
        }
    }

    public Flux<String> stream(CopilotRequest request) {
        String validationError = validateRequest(request);
        if (validationError != null) {
            return Flux.just(validationError, "[DONE] {\"type\":\"done\"}");
        }

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

                boolean inContent = false;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
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

                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    sink.tryEmitError(new IllegalStateException("gh copilot stream command timed out"));
                    return;
                }

                if (process.exitValue() != 0) {
                    sink.tryEmitError(new IllegalStateException("gh copilot stream command failed with exit code " + process.exitValue()));
                    return;
                }

                sink.tryEmitNext("[DONE] {\"type\":\"done\"}");
                sink.tryEmitComplete();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("[COPILOT STREAM] Interrupted", e);
                sink.tryEmitError(e);
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
        cmd.add(ghCommand);
        cmd.add("copilot");

        String mode = resolveMode(request);
        if ("explain".equals(mode)) {
            cmd.add("explain");
            addRequestedModel(cmd, request);
            cmd.add("-p");
            cmd.add(request.message().trim());
        } else {
            cmd.add("suggest");
            String target = resolveTarget(request);
            cmd.add("-t");
            cmd.add(target);
            addRequestedModel(cmd, request);
            cmd.add("-p");
            cmd.add(request.message().trim());
        }
        return cmd;
    }

    private void addRequestedModel(List<String> cmd, CopilotRequest request) {
        if (request != null && request.model() != null && !request.model().isBlank()) {
            cmd.add("--model");
            cmd.add(request.model().trim());
        }
    }

    private String parseOutput(String raw) {
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
        if (output.isBlank()) {
            output = sanitizeRawOutput(raw).strip();
        }
        return output.isBlank() ? "(gh copilot no devolvió sugerencia)" : output;
    }

    private String sanitizeRawOutput(String raw) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : raw.split("\\R")) {
            String normalized = stripAnsi(line).stripTrailing();
            if (!normalized.isBlank() && !normalized.startsWith("? ")) {
                cleaned.append(normalized).append("\n");
            }
        }
        return cleaned.toString();
    }

    private String stripAnsi(String text) {
        return ANSI_PATTERN.matcher(text).replaceAll("").replace("\r", "");
    }

    private boolean isTuiLine(String line) {
        if (line.isBlank()) return true;
        // Filter obvious TUI/control characters
        return line.contains("\u001B") || line.matches("^[\\s╭╰│─╯╮✓✗●○◉◎►]+.*");
    }

    private String validateRequest(CopilotRequest request) {
        if (request == null) {
            return "Invalid request: request body is required";
        }
        if (request.message() == null || request.message().isBlank()) {
            return "Invalid request: message is required";
        }
        String mode = resolveMode(request);
        if (!"suggest".equals(mode) && !"explain".equals(mode)) {
            return "Invalid request: mode must be 'suggest' or 'explain'";
        }
        return null;
    }

    private String resolveMode(CopilotRequest request) {
        if (request == null || request.mode() == null || request.mode().isBlank()) {
            return "suggest";
        }
        return request.mode().trim().toLowerCase(Locale.ROOT);
    }

    private String resolveTarget(CopilotRequest request) {
        if (request == null || request.target() == null || request.target().isBlank()) {
            return "shell";
        }
        return request.target().trim().toLowerCase(Locale.ROOT);
    }

    private String readStdout(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().reduce((left, right) -> left + System.lineSeparator() + right).orElse("");
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read gh copilot stdout", ex);
        }
    }

    private CommandResult runCommand(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            boolean completed = process.waitFor(8, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new CommandResult(-1, "Command timed out");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return new CommandResult(process.exitValue(), output);
        } catch (Exception ex) {
            return new CommandResult(-1, ex.getMessage() == null ? "Unknown error" : ex.getMessage());
        }
    }

    private String summarize(String output, String fallback) {
        if (output == null || output.isBlank()) {
            return fallback;
        }
        String firstLine = output.lines().findFirst().orElse(fallback).trim();
        return firstLine.isBlank() ? fallback : firstLine;
    }

    private record CommandResult(int exitCode, String output) {
    }

    private record AuthStatusCache(CliProviderStatus status, long expiresAtMillis) {
    }

    private List<String> loadModels() {
        CommandResult help = runCommand(List.of(ghCommand, "copilot", "--", "--help"));
        LinkedHashSet<String> models = new LinkedHashSet<>();
        models.add("default");
        if (help.exitCode == 0) {
            String output = help.output;
            if (output.contains("--model")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile("--model\\s+([A-Za-z0-9._:-]+)")
                        .matcher(output);
                while (matcher.find()) {
                    String value = matcher.group(1).trim();
                    if (!value.equalsIgnoreCase("<model>")) {
                        models.add(value);
                    }
                }
            }
        }
        return new ArrayList<>(models);
    }

    private record ModelListCache(List<String> models, long expiresAtMillis) {
    }
}

