package es.com.adakadavra.agent.jarvis.api;

import es.com.adakadavra.agent.jarvis.agent.OrchestratorAgent;
import es.com.adakadavra.agent.jarvis.model.AgentRequest;
import es.com.adakadavra.agent.jarvis.model.AgentResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/jarvis")
public class JarvisController {

    private final OrchestratorAgent orchestrator;

    public JarvisController(OrchestratorAgent orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@RequestBody AgentRequest request) {
        return orchestrator.process(request.message(), request.conversationId());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody AgentRequest request) {
        return orchestrator.stream(request.message(), request.conversationId());
    }
}
