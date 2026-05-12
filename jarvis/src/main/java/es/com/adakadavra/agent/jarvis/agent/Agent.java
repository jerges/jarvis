package es.com.adakadavra.agent.jarvis.agent;

import es.com.adakadavra.agent.jarvis.model.AgentExecutionResult;
import es.com.adakadavra.agent.jarvis.model.AgentType;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface Agent {
    AgentType type();

    String process(String request, String conversationId, ModelProvider provider, String requestedModel);

    default Mono<String> processReactive(String request, String conversationId, ModelProvider provider, String requestedModel) {
        return Mono.fromCallable(() -> process(request, conversationId, provider, requestedModel))
                .subscribeOn(Schedulers.boundedElastic());
    }

    default Mono<AgentExecutionResult> processWithMetadataReactive(
            String request,
            String conversationId,
            ModelProvider provider,
            String requestedModel) {
        return processReactive(request, conversationId, provider, requestedModel)
                .map(content -> new AgentExecutionResult(content, requestedModel));
    }

    Flux<String> stream(String request, String conversationId, ModelProvider provider, String requestedModel);
}
