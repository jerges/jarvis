package es.com.adakadavra.agent.jarvis.api;

import es.com.adakadavra.agent.jarvis.cli.ClaudeCliService;
import es.com.adakadavra.agent.jarvis.model.CliRequest;
import es.com.adakadavra.agent.jarvis.model.CliResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Endpoints que invocan el Claude CLI directamente, sin pasar por el orquestador de agentes.
 *
 * <pre>
 * POST /api/jarvis/cli         → chat síncrono, retorna CliResponse con sessionId para continuar
 * POST /api/jarvis/cli/stream  → streaming SSE; el último evento es "[DONE] {...json...}"
 * </pre>
 */
@RestController
@RequestMapping("/api/jarvis/cli")
public class ClaudeCliController {

    private final ClaudeCliService cliService;

    public ClaudeCliController(ClaudeCliService cliService) {
        this.cliService = cliService;
    }

    /**
     * Chat síncrono via Claude CLI.
     *
     * <p>Request example:
     * <pre>
     * {
     *   "message": "Explícame el patrón CQRS",
     *   "sessionId": null,
     *   "model": "sonnet"
     * }
     * </pre>
     *
     * <p>Response includes {@code sessionId} — pass it back in subsequent requests
     * to continue the same conversation.
     */
    @PostMapping
    public Mono<CliResponse> chat(@RequestBody CliRequest request) {
        return cliService.chat(request);
    }

    /**
     * Streaming SSE via Claude CLI.
     *
     * <p>Emite tokens de texto conforme el modelo los genera.
     * El último evento tiene el formato: {@code [DONE] {"type":"done","sessionId":"...","costUsd":0.0}}
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody CliRequest request) {
        return cliService.stream(request);
    }
}
