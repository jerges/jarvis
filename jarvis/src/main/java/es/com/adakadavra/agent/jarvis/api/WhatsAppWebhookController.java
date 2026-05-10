package es.com.adakadavra.agent.jarvis.api;

import com.fasterxml.jackson.databind.JsonNode;
import es.com.adakadavra.agent.jarvis.messaging.WhatsAppService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookController {

    private final WhatsAppService whatsAppService;

    @Value("${jarvis.whatsapp.verify-token:}")
    private String verifyToken;

    public WhatsAppWebhookController(WhatsAppService whatsAppService) {
        this.whatsAppService = whatsAppService;
    }

    // Meta llama a este endpoint al registrar el webhook para verificar la URL
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(value = "hub.mode", required = false, defaultValue = "") String mode,
            @RequestParam(value = "hub.challenge", required = false, defaultValue = "") String challenge,
            @RequestParam(value = "hub.verify_token", required = false, defaultValue = "") String token) {

        if ("subscribe".equals(mode) && !verifyToken.isBlank() && verifyToken.equals(token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // Meta envía los mensajes entrantes a este endpoint
    @PostMapping
    public ResponseEntity<Void> handleMessage(@RequestBody JsonNode payload) {
        // Retorna 200 inmediatamente; el procesamiento ocurre en background (@Async)
        whatsAppService.handlePayload(payload);
        return ResponseEntity.ok().build();
    }
}
