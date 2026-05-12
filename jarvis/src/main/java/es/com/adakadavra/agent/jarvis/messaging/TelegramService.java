package es.com.adakadavra.agent.jarvis.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import es.com.adakadavra.agent.jarvis.agent.DirectorAgent;
import es.com.adakadavra.agent.jarvis.model.AgentRequest;
import es.com.adakadavra.agent.jarvis.model.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);
    private static final int MAX_TELEGRAM_MESSAGE_LENGTH = 4096;

    private final DirectorAgent orchestrator;
    private final RestClient restClient;

    @Value("${jarvis.telegram.bot-token:}")
    private String botToken;

    public TelegramService(
            DirectorAgent orchestrator,
            @Qualifier("telegramRestClient") RestClient restClient) {
        this.orchestrator = orchestrator;
        this.restClient = restClient;
    }

    @Async
    public void handleUpdate(JsonNode update) {
        JsonNode message = update.path("message");
        if (message.isMissingNode() || message.isNull()) return;

        String chatId = message.path("chat").path("id").asText();
        String text = message.path("text").asText("").trim();

        if (text.isBlank()) return;

        if (text.startsWith("/start")) {
            sendMessage(chatId,
                    "¡Hola! Soy *Jarvis*, tu asistente personal con IA.\n\n" +
                            "Puedo ayudarte con:\n" +
                            "• 💻 Código y arquitectura de software\n" +
                            "• ⚙️ DevOps, Kubernetes y cloud\n" +
                            "• 📱 Redes sociales y mensajería\n" +
                            "• 🎨 Frontend y diseño web\n\n" +
                            "¡Escríbeme lo que necesites!");
            return;
        }

        try {
            sendChatAction(chatId, "typing");
            AgentResponse response = orchestrator.process(
                    new AgentRequest(text, "telegram-" + chatId, null, null));
            sendMessage(chatId, response.response());
        } catch (Exception e) {
            log.error("Error processing Telegram message for chat {}", chatId, e);
            sendMessage(chatId, "Lo siento, ocurrió un error al procesar tu mensaje. Por favor, inténtalo de nuevo.");
        }
    }

    private void sendMessage(String chatId, String text) {
        for (String chunk : chunk(text, MAX_TELEGRAM_MESSAGE_LENGTH)) {
            try {
                restClient.post()
                        .uri("/bot{token}/sendMessage", botToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("chat_id", chatId, "text", chunk, "parse_mode", "Markdown"))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                // Fallback sin Markdown si el formato falla
                try {
                    restClient.post()
                            .uri("/bot{token}/sendMessage", botToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("chat_id", chatId, "text", chunk))
                            .retrieve()
                            .toBodilessEntity();
                } catch (Exception ex) {
                    log.error("Failed to send Telegram message to chat {}: {}", chatId, ex.getMessage());
                }
            }
        }
    }

    private void sendChatAction(String chatId, String action) {
        try {
            restClient.post()
                    .uri("/bot{token}/sendChatAction", botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", chatId, "action", action))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.debug("Could not send chat action to {}: {}", chatId, e.getMessage());
        }
    }

    private List<String> chunk(String text, int maxLen) {
        if (text.length() <= maxLen) return List.of(text);
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < text.length(); i += maxLen) {
            parts.add(text.substring(i, Math.min(i + maxLen, text.length())));
        }
        return parts;
    }
}
