package es.com.adakadavra.agent.jarvis.model;

public record AgentResponse(AgentType routedTo, String reasoning, String response, ModelProvider provider) {}
