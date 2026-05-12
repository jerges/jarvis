package es.com.adakadavra.agent.jarvis.api;

import es.com.adakadavra.agent.jarvis.copilot.CopilotCliService;
import es.com.adakadavra.agent.jarvis.model.CopilotRequest;
import es.com.adakadavra.agent.jarvis.model.CopilotResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/jarvis/copilot")
public class CopilotCliController {

    private final CopilotCliService copilotCliService;

    public CopilotCliController(CopilotCliService copilotCliService) {
        this.copilotCliService = copilotCliService;
    }

    @PostMapping
    public Mono<CopilotResponse> chat(@RequestBody CopilotRequest request) {
        return Mono.fromCallable(() -> copilotCliService.execute(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody CopilotRequest request) {
        return copilotCliService.stream(request);
    }
}
