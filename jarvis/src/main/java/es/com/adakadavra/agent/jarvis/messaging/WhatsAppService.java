package es.com.adakadavra.agent.jarvis.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import es.com.adakadavra.agent.jarvis.agent.JarvisAgent;
import es.com.adakadavra.agent.jarvis.model.AgentRequest;
import es.com.adakadavra.agent.jarvis.model.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);
    private static final int MAX_WHATSAPP_MESSAGE_LENGTH = 4096;

    private final JarvisAgent orchestrator;
    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://graph.facebook.com/v21.0")
            .build();

    @Value("${jarvis.whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${jarvis.whatsapp.access-token:}")
    private String accessToken;

    public WhatsAppService(JarvisAgent orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Async
    public void handlePayload(JsonNode payload) {
        JsonNode entries = payload.path("entry");
        if (!entries.isArray()) return;

        for (JsonNode entry : entries) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode messages = change.path("value").path("messages");
                if (!messages.isArray()) continue;
                for (JsonNode message : messages) {
                    processMessage(message);
                }
            }
        }
    }

    private void processMessage(JsonNode message) {
        // Solo procesamos mensajes de texto
        if (!"text".equals(message.path("type").asText())) return;

        String from = message.path("from").asText();
        String text = message.path("text").path("body").asText("").trim();

        if (text.isBlank()) return;

        try {
            AgentResponse response = orchestrator.process(
                    new AgentRequest(text, "whatsapp-" + from, null, null, null, null));
            sendMessage(from, response.response());
        } catch (Exception e) {
            log.error("Error processing WhatsApp message from {}", from, e);
            sendMessage(from, "Lo siento, ocurrió un error al procesar tu mensaje. Por favor, inténtalo de nuevo.");
        }
    }

    private void sendMessage(String to, String text) {
        String body = text.length() > MAX_WHATSAPP_MESSAGE_LENGTH
                ? text.substring(0, MAX_WHATSAPP_MESSAGE_LENGTH - 3) + "..."
                : text;
        try {
            restClient.post()
                    .uri("/{phoneNumberId}/messages", phoneNumberId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + accessToken)
                    .body(Map.of(
                            "messaging_product", "whatsapp",
                            "to", to,
                            "type", "text",
                            "text", Map.of("body", body)
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}: {}", to, e.getMessage());
        }
    }
}
