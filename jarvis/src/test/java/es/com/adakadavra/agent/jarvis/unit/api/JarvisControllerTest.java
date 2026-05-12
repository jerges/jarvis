package es.com.adakadavra.agent.jarvis.unit.api;

import es.com.adakadavra.agent.jarvis.agent.OrchestratorAgent;
import es.com.adakadavra.agent.jarvis.api.JarvisController;
import es.com.adakadavra.agent.jarvis.model.AgentRequest;
import es.com.adakadavra.agent.jarvis.model.AgentResponse;
import es.com.adakadavra.agent.jarvis.model.AgentType;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JarvisController.class)
class JarvisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrchestratorAgent orchestratorAgent;

    @Test
    void chatReturns200WithRoutedResponse() throws Exception {
        var response = new AgentResponse(AgentType.DEVELOPER, "dev question", "Here is the answer", ModelProvider.ANTHROPIC);
        when(orchestratorAgent.process(any(AgentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/jarvis/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "How do I write a Java stream?", "conversationId": "test-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routedTo").value("DEVELOPER"))
                .andExpect(jsonPath("$.reasoning").value("dev question"))
                .andExpect(jsonPath("$.response").value("Here is the answer"))
                .andExpect(jsonPath("$.provider").value("ANTHROPIC"));
    }

    @Test
    void chatWithAzureProviderRoutes() throws Exception {
        var response = new AgentResponse(AgentType.DEVOPS, "devops", "Deploy with kubectl", ModelProvider.AZURE);
        when(orchestratorAgent.process(any(AgentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/jarvis/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Deploy to Kubernetes", "conversationId": "k8s-1", "provider": "AZURE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routedTo").value("DEVOPS"))
                .andExpect(jsonPath("$.provider").value("AZURE"));
    }

    @Test
    void chatWithoutConversationIdAccepted() throws Exception {
        var response = new AgentResponse(AgentType.FRONTEND, "frontend", "Use flexbox", ModelProvider.ANTHROPIC);
        when(orchestratorAgent.process(any(AgentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/jarvis/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "How to center a div?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routedTo").value("FRONTEND"));
    }

    @Test
    void streamReturnsServerSentEvents() throws Exception {
        when(orchestratorAgent.stream(any(AgentRequest.class)))
                .thenReturn(Flux.just("Hello", " there", " Jarvis"));

        mockMvc.perform(post("/api/jarvis/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Hello", "conversationId": "stream-1"}
                                """))
                .andExpect(status().isOk());
    }
}
