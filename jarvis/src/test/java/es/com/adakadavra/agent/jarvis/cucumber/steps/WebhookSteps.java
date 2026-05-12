package es.com.adakadavra.agent.jarvis.cucumber.steps;

import com.fasterxml.jackson.databind.JsonNode;
import es.com.adakadavra.agent.jarvis.messaging.TelegramService;
import es.com.adakadavra.agent.jarvis.messaging.WhatsAppService;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class WebhookSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private WhatsAppService whatsAppService;

    private ResultActions result;

    @Before
    void resetMocks() {
        reset(telegramService, whatsAppService);
    }

    // ── Telegram ──────────────────────────────────────────────────────────────

    @When("llega un webhook de Telegram con secreto {string}")
    public void llegaUnWebhookDeTelegramConSecreto(String secret) throws Exception {
        result = mockMvc.perform(post("/webhook/telegram")
                .header("X-Telegram-Bot-Api-Secret-Token", secret)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"update_id": 1, "message": {"chat": {"id": 123}, "text": "Hola"}}
                        """));
    }

    @When("llega un webhook de Telegram sin cabecera de secreto")
    public void llegaUnWebhookDeTelegramSinCabeceraDeSecreto() throws Exception {
        result = mockMvc.perform(post("/webhook/telegram")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"update_id": 1, "message": {"chat": {"id": 123}, "text": "Hola"}}
                        """));
    }

    @Then("el servicio de Telegram procesa el update")
    public void elServicioDeTelegramProcesaElUpdate() throws Exception {
        result.andExpect(status().isOk());
        verify(telegramService).handleUpdate(any(JsonNode.class));
    }

    @Then("el servicio de Telegram no procesa el update")
    public void elServicioDeTelegramNoProcesaElUpdate() throws Exception {
        result.andExpect(status().isUnauthorized());
        verify(telegramService, never()).handleUpdate(any());
    }

    // ── WhatsApp ──────────────────────────────────────────────────────────────

    @When("Meta verifica el webhook de WhatsApp con token {string} y challenge {string}")
    public void metaVerificaElWebhookDeWhatsAppConToken(String token, String challenge) throws Exception {
        result = mockMvc.perform(get("/webhook/whatsapp")
                .param("hub.mode", "subscribe")
                .param("hub.verify_token", token)
                .param("hub.challenge", challenge));
    }

    @When("llega un mensaje de WhatsApp")
    public void llegaUnMensajeDeWhatsApp() throws Exception {
        result = mockMvc.perform(post("/webhook/whatsapp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "entry": [{
                            "changes": [{
                              "value": {
                                "messages": [{"type": "text", "from": "34600000000", "text": {"body": "Hola"}}]
                              }
                            }]
                          }]
                        }
                        """));
    }

    @Then("la respuesta contiene el challenge {string}")
    public void laRespuestaContieneElChallenge(String challenge) throws Exception {
        result.andExpect(status().isOk())
              .andExpect(content().string(challenge));
    }

    @Then("la verificación de WhatsApp es rechazada")
    public void laVerificaciónDeWhatsAppEsRechazada() throws Exception {
        result.andExpect(status().isForbidden());
    }

    @Then("el servicio de WhatsApp procesa el mensaje")
    public void elServicioDeWhatsAppProcesaElMensaje() throws Exception {
        result.andExpect(status().isOk());
        verify(whatsAppService).handlePayload(any(JsonNode.class));
    }

    // ── Compartido ────────────────────────────────────────────────────────────

    @Given("el secreto de Telegram configurado es {string}")
    public void elSecretoDeTelegramConfiguradoEs(String secret) {
        // El secreto se lee de application-test.properties (test-webhook-secret)
        // Este step documenta el contexto del escenario
    }
}
