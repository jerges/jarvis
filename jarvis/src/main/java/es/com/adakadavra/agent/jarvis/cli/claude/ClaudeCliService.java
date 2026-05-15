package es.com.adakadavra.agent.jarvis.cli.claude;

import es.com.adakadavra.agent.jarvis.model.CliProviderStatus;
import es.com.adakadavra.agent.jarvis.model.CopilotRequest;
import es.com.adakadavra.agent.jarvis.model.CopilotResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ClaudeCliService {

    private static final String PROVIDER = "claude";

    private final ClaudeCliClient claudeCliClient;
    private final String claudeCommand;
    private final String defaultModel;
    private final String orchestratorModel;
    private final long authCacheSeconds;

    private final AtomicReference<AuthStatusCache> authStatusCache = new AtomicReference<>();
    private final AtomicReference<ModelListCache> modelListCache = new AtomicReference<>();

    public ClaudeCliService(
            ClaudeCliClient claudeCliClient,
            @Value("${jarvis.claude-cli.command:claude}") String claudeCommand,
            @Value("${jarvis.claude-cli.orchestrator-model:haiku}") String orchestratorModel,
            @Value("${jarvis.claude-cli.agent-model:haiku}") String defaultModel,
            @Value("${jarvis.claude-cli.auth-cache-seconds:60}") long authCacheSeconds) {
        this.claudeCliClient = claudeCliClient;
        this.claudeCommand = claudeCommand;
        this.orchestratorModel = orchestratorModel;
        this.defaultModel = defaultModel;
        this.authCacheSeconds = Math.max(0, authCacheSeconds);
    }

    public List<String> listModels() {
        long now = System.currentTimeMillis();
        ModelListCache cached = modelListCache.get();
        if (cached != null && now < cached.expiresAtMillis()) {
            return cached.models();
        }

        List<String> models = loadModels();
        modelListCache.set(new ModelListCache(models, now + (authCacheSeconds * 1000)));
        return models;
    }

    public CopilotResponse execute(CopilotRequest request) {
        ensureAuthenticated();
        String mode = resolveMode(request);
        ClaudeCliResponse response = claudeCliClient.execute(
                systemPrompt(mode, request.target()),
                request.message().trim(),
                defaultModel);
        return new CopilotResponse(response.content(), mode);
    }

    public Flux<String> stream(CopilotRequest request) {
        ensureAuthenticated();
        String mode = resolveMode(request);
        return claudeCliClient.stream(
                systemPrompt(mode, request.target()),
                request.message().trim(),
                defaultModel);
    }

    public CliProviderStatus providerStatus() {
        CliProviderStatus cached = getCachedProviderStatus();
        if (cached != null) {
            return cached;
        }

        CommandResult authStatus = runCommand(List.of(claudeCommand, "auth", "status"));
        if (authStatus.exitCode == 0) {
            CliProviderStatus status = new CliProviderStatus(PROVIDER, true, true, "Claude CLI authenticated");
            cacheProviderStatus(status);
            return status;
        }

        String output = authStatus.output.toLowerCase(Locale.ROOT);
        if (output.contains("not logged") || output.contains("login") || output.contains("auth")) {
            CliProviderStatus status = new CliProviderStatus(PROVIDER, true, false, summarize(authStatus.output, "Claude CLI is not authenticated"));
            cacheProviderStatus(status);
            return status;
        }

        if (output.contains("unknown") || output.contains("invalid") || output.contains("usage:")) {
            // Fallback for CLI versions without `auth status` subcommand.
            CommandResult fallback = runCommand(List.of(claudeCommand, "--version"));
            if (fallback.exitCode == 0) {
                CliProviderStatus status = new CliProviderStatus(PROVIDER, true, false, "Claude CLI available, authentication status unknown");
                cacheProviderStatus(status);
                return status;
            }
        }

        CliProviderStatus status = new CliProviderStatus(PROVIDER, false, false, summarize(authStatus.output, "Claude CLI is not available"));
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
        long expiresAtMillis = System.currentTimeMillis() + (authCacheSeconds * 1000);
        authStatusCache.set(new AuthStatusCache(status, expiresAtMillis));
    }

    private void ensureAuthenticated() {
        CliProviderStatus status = providerStatus();
        if (!status.available()) {
            throw new IllegalStateException(status.message());
        }
        if (!status.authenticated()) {
            throw new IllegalStateException(status.message());
        }
    }

    private String resolveMode(CopilotRequest request) {
        if (request.mode() == null || request.mode().isBlank()) {
            return "suggest";
        }
        return request.mode().trim().toLowerCase(Locale.ROOT);
    }

    private String systemPrompt(String mode, String target) {
        if ("explain".equals(mode)) {
            return "Explain the command in a concise way. Include what it does, risks, and a safer variant when relevant.";
        }

        String resolvedTarget = target == null || target.isBlank() ? "shell" : target.trim().toLowerCase(Locale.ROOT);
        return switch (resolvedTarget) {
            case "git" -> "Return only a git command matching the request, without markdown or extra prose.";
            case "gh" -> "Return only a GitHub CLI (gh) command matching the request, without markdown or extra prose.";
            default -> "Return only a shell command matching the request, without markdown or extra prose.";
        };
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
        CommandResult help = runCommand(List.of(claudeCommand, "--help"));
        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (help.exitCode == 0) {
            String output = help.output.toLowerCase(Locale.ROOT);
            if (output.contains("--model")) {
                models.add("haiku");
                models.add("sonnet");
                models.add("opus");
            }
        }
        addIfPresent(models, orchestratorModel);
        addIfPresent(models, defaultModel);
        if (models.isEmpty()) {
            models.add("haiku");
            models.add("sonnet");
            models.add("opus");
        }
        return new ArrayList<>(models);
    }

    private void addIfPresent(LinkedHashSet<String> models, String value) {
        if (value != null && !value.isBlank()) {
            models.add(value.trim());
        }
    }

    private record ModelListCache(List<String> models, long expiresAtMillis) {
    }
}

