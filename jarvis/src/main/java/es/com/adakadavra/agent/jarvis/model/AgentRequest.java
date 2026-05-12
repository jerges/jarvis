package es.com.adakadavra.agent.jarvis.model;

public record AgentRequest(String message, String conversationId, ModelProvider provider, String model) {}
