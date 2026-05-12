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

/**
 * Endpoints que invocan el GitHub Copilot CLI directamente.
 *
 * <pre>
 * POST /api/jarvis/copilot         → chat síncrono (suggest o explain)
 * POST /api/jarvis/copilot/stream  → streaming SSE; último evento es "[DONE] {...}"
 * </pre>
 */
@RestController
@RequestMapping("/api/jarvis/copilot")
public class CopilotCliController {

    private final CopilotCliService copilotService;

    public CopilotCliController(CopilotCliService copilotService) {
        this.copilotService = copilotService;
    }

    /**
     * Chat síncrono via GitHub Copilot CLI.
     *
     * <p>Ejemplo de request:
     * <pre>
     * {
     *   "message": "list all docker containers sorted by name",
     *   "mode": "suggest",
     *   "target": "shell"
     * }
     * </pre>
     */
    @PostMapping
    public Mono<CopilotResponse> chat(@RequestBody CopilotRequest request) {
        return copilotService.chat(request);
    }

    /**
     * Streaming SSE via GitHub Copilot CLI.
     * El último evento tiene el formato: {@code [DONE] {"type":"done"}}
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody CopilotRequest request) {
        return copilotService.stream(request);
    }
}
