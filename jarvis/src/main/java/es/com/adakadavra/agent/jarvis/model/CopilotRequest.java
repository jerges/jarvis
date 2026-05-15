package es.com.adakadavra.agent.jarvis.model;

public record CopilotRequest(String message, String mode, String target, String provider, String model) {}
