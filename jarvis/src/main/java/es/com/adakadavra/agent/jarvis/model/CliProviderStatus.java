package es.com.adakadavra.agent.jarvis.model;

public record CliProviderStatus(String provider, boolean available, boolean authenticated, String message) {
}

