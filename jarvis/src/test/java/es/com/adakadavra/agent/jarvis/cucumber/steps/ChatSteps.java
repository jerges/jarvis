package es.com.adakadavra.agent.jarvis.cucumber.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.com.adakadavra.agent.jarvis.agent.OrchestratorAgent;
import es.com.adakadavra.agent.jarvis.model.AgentRequest;
import es.com.adakadavra.agent.jarvis.model.AgentResponse;
import es.com.adakadavra.agent.jarvis.model.AgentType;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ChatSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrchestratorAgent orchestratorAgent;

    private ResultActions result;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Before
    void resetMocks() {
        reset(orchestratorAgent);
    }

    @Given("el orquestador responde con agente {string} y mensaje {string}")
    public void elOrquestadorRespondeConAgente(String agentType, String message) {
        var response = new AgentResponse(
                AgentType.valueOf(agentType),
                "Classified as " + agentType,
                message,
                ModelProvider.ANTHROPIC);
        when(orchestratorAgent.process(any(AgentRequest.class))).thenReturn(response);
    }

    @Given("el orquestador responde con agente {string} usando Azure")
    public void elOrquestadorRespondeConAgenteAzure(String agentType) {
        var response = new AgentResponse(
                AgentType.valueOf(agentType),
                "Azure routing",
                "Azure response",
                ModelProvider.AZURE);
        when(orchestratorAgent.process(any(AgentRequest.class))).thenReturn(response);
    }

    @When("envío un mensaje de chat {string} con conversationId {string}")
    public void envíoUnMensajeDeChatConConversationId(String message, String conversationId) throws Exception {
        String body = objectMapper.writeValueAsString(
                new AgentRequest(message, conversationId, null));
        result = mockMvc.perform(post("/api/jarvis/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @When("envío un mensaje de chat {string} con provider {string}")
    public void envíoUnMensajeDeChatConProvider(String message, String provider) throws Exception {
        String body = objectMapper.writeValueAsString(
                new AgentRequest(message, "conv-test", ModelProvider.valueOf(provider)));
        result = mockMvc.perform(post("/api/jarvis/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @When("envío una petición de chat vacía")
    public void envíoUnaPeticiónDeChatVacía() throws Exception {
        result = mockMvc.perform(post("/api/jarvis/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    @Then("la respuesta HTTP es {int}")
    public void laRespuestaHTTPEs(int statusCode) throws Exception {
        result.andExpect(status().is(statusCode));
    }

    @Then("el campo routedTo es {string}")
    public void elCampoRoutedToEs(String agentType) throws Exception {
        result.andExpect(jsonPath("$.routedTo").value(agentType));
    }

    @Then("el campo provider es {string}")
    public void elCampoProviderEs(String provider) throws Exception {
        result.andExpect(jsonPath("$.provider").value(provider));
    }

    @Then("la respuesta contiene un campo response no vacío")
    public void laRespuestaContieneUnCampoResponseNoVacío() throws Exception {
        result.andExpect(jsonPath("$.response").isNotEmpty());
    }

    @Then("la respuesta contiene un campo reasoning")
    public void laRespuestaContieneUnCampoReasoning() throws Exception {
        result.andExpect(jsonPath("$.reasoning").exists());
    }
}
