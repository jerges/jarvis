package es.com.adakadavra.agent.jarvis.model;

public record CopilotRequest(
        String message,
        String mode,    // "suggest" | "explain"
        String target   // "shell" | "git" | "gh"  (solo para suggest)
) {}
