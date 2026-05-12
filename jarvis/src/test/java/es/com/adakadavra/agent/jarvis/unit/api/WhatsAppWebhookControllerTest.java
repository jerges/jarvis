package es.com.adakadavra.agent.jarvis.unit.api;

import es.com.adakadavra.agent.jarvis.api.WhatsAppWebhookController;
import es.com.adakadavra.agent.jarvis.messaging.WhatsAppService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WhatsAppWebhookController.class)
@TestPropertySource(properties = "jarvis.whatsapp.verify-token=test-verify-token")
class WhatsAppWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WhatsAppService whatsAppService;

    private static final String PAYLOAD_BODY = """
            {
              "entry": [{
                "changes": [{
                  "value": {
                    "messages": [{"type": "text", "from": "34600000000", "text": {"body": "Hola"}}]
                  }
                }]
              }]
            }
            """;

    @Test
    void verifyReturns200AndChallengeWithCorrectToken() throws Exception {
        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "test-verify-token")
                        .param("hub.challenge", "challenge-abc-123"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge-abc-123"));
    }

    @Test
    void verifyReturns403WithWrongToken() throws Exception {
        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong-token")
                        .param("hub.challenge", "challenge-abc-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifyReturns403WhenModeIsNotSubscribe() throws Exception {
        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "unsubscribe")
                        .param("hub.verify_token", "test-verify-token")
                        .param("hub.challenge", "challenge-abc-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void handleMessageReturns200AndDelegatesToService() throws Exception {
        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_BODY))
                .andExpect(status().isOk());

        verify(whatsAppService).handlePayload(any());
    }
}
