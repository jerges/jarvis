package es.com.adakadavra.agent.jarvis.api;

import com.fasterxml.jackson.databind.JsonNode;
import es.com.adakadavra.agent.jarvis.messaging.TelegramService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook/telegram")
public class TelegramWebhookController {

    private final TelegramService telegramService;

    @Value("${jarvis.telegram.webhook-secret:}")
    private String webhookSecret;

    public TelegramWebhookController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @PostMapping
    public ResponseEntity<Void> handleUpdate(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody JsonNode update) {

        if (!webhookSecret.isBlank() && !webhookSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Retorna 200 inmediatamente; el procesamiento ocurre en background (@Async)
        telegramService.handleUpdate(update);
        return ResponseEntity.ok().build();
    }
}
