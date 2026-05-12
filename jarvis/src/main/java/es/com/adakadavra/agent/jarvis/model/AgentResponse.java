package es.com.adakadavra.agent.jarvis.model;

public record AgentResponse(
        AgentType routedTo,
        String reasoning,
        String response,
        ModelProvider provider,
        String modelUsed,
        TokenMetadata tokens) {

    // Constructor con valores por defecto para backward compatibility
    public AgentResponse(AgentType routedTo, String reasoning, String response, ModelProvider provider, String modelUsed) {
        this(routedTo, reasoning, response, provider, modelUsed, null);
    }
}
