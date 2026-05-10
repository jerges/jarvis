package es.com.adakadavra.agent.jarvis.agent;

import es.com.adakadavra.agent.jarvis.model.AgentType;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import reactor.core.publisher.Flux;

public interface Agent {
    AgentType type();
    String process(String request, String conversationId, ModelProvider provider);
    Flux<String> stream(String request, String conversationId, ModelProvider provider);
}
