package es.com.adakadavra.agent.jarvis.api;

import es.com.adakadavra.agent.jarvis.cli.claude.ClaudeCliService;
import es.com.adakadavra.agent.jarvis.cli.copilot.CopilotCliService;
import es.com.adakadavra.agent.jarvis.model.CliProviderStatus;
import es.com.adakadavra.agent.jarvis.model.CopilotRequest;
import es.com.adakadavra.agent.jarvis.model.CopilotResponse;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/jarvis/copilot")
public class CopilotCliController {

    private final CopilotCliService copilotCliService;
    private final ClaudeCliService claudeCliService;

    public CopilotCliController(CopilotCliService copilotCliService, ClaudeCliService claudeCliService) {
        this.copilotCliService = copilotCliService;
        this.claudeCliService = claudeCliService;
    }

    @GetMapping("/providers")
    public List<CliProviderStatus> providerStatus() {
        return List.of(copilotCliService.providerStatus(), claudeCliService.providerStatus());
    }

    @PostMapping
    public Mono<CopilotResponse> chat(@RequestBody CopilotRequest request) {
        return Mono.fromCallable(() -> executeByProvider(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody CopilotRequest request) {
        return streamByProvider(request);
    }

    private CopilotResponse executeByProvider(CopilotRequest request) {
        String provider = resolveProvider(request);
        CliProviderStatus status = resolveStatus(provider);
        ensureProviderReady(status);

        if ("claude".equals(provider)) {
            return claudeCliService.execute(request);
        }
        return copilotCliService.execute(request);
    }

    private Flux<String> streamByProvider(CopilotRequest request) {
        String provider = resolveProvider(request);
        CliProviderStatus status = resolveStatus(provider);
        ensureProviderReady(status);

        if ("claude".equals(provider)) {
            return claudeCliService.stream(request);
        }
        return copilotCliService.stream(request);
    }

    private CliProviderStatus resolveStatus(String provider) {
        return "claude".equals(provider) ? claudeCliService.providerStatus() : copilotCliService.providerStatus();
    }

    private void ensureProviderReady(CliProviderStatus status) {
        if (!status.available()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, status.message());
        }
        if (!status.authenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, status.message());
        }
    }

    private String resolveProvider(CopilotRequest request) {
        if (request == null || request.provider() == null || request.provider().isBlank()) {
            return "copilot";
        }

        String provider = request.provider().trim().toLowerCase(Locale.ROOT);
        if ("copilot".equals(provider) || "claude".equals(provider)) {
            return provider;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported CLI provider: " + request.provider());
    }
}
