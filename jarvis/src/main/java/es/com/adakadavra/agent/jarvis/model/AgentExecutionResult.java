package es.com.adakadavra.agent.jarvis.model;

public record AgentExecutionResult(
        String response,
        String modelUsed,
        TokenMetadata tokens) {
    
    // Constructor con valores por defecto para backward compatibility
    public AgentExecutionResult(String response, String modelUsed) {
        this(response, modelUsed, null);
    }
}


