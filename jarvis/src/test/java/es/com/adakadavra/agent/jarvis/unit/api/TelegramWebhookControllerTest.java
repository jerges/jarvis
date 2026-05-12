package es.com.adakadavra.agent.jarvis.unit.api;

import es.com.adakadavra.agent.jarvis.api.TelegramWebhookController;
import es.com.adakadavra.agent.jarvis.messaging.TelegramService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelegramWebhookController.class)
@TestPropertySource(properties = "jarvis.telegram.webhook-secret=test-webhook-secret")
class TelegramWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelegramService telegramService;

    private static final String UPDATE_BODY = """
            {"update_id": 1, "message": {"chat": {"id": 123}, "text": "Hello"}}
            """;

    @Test
    void returns200WithValidSecretToken() throws Exception {
        mockMvc.perform(post("/webhook/telegram")
                        .header("X-Telegram-Bot-Api-Secret-Token", "test-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isOk());

        verify(telegramService).handleUpdate(any());
    }

    @Test
    void returns401WithInvalidSecretToken() throws Exception {
        mockMvc.perform(post("/webhook/telegram")
                        .header("X-Telegram-Bot-Api-Secret-Token", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isUnauthorized());

        verify(telegramService, never()).handleUpdate(any());
    }

    @Test
    void returns401WithMissingSecretHeader() throws Exception {
        // Secreto configurado pero sin cabecera → debe rechazar
        mockMvc.perform(post("/webhook/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isUnauthorized());

        verify(telegramService, never()).handleUpdate(any());
    }
}
