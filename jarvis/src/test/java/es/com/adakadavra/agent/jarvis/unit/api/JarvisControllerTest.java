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
        var response = new AgentResponse(AgentType.DEVELOPER, "dev question", "Here is the answer",
                ModelProvider.ANTHROPIC, "claude-sonnet-4-6", 120, 350);
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
                .andExpect(jsonPath("$.provider").value("ANTHROPIC"))
                .andExpect(jsonPath("$.model").value("claude-sonnet-4-6"))
                .andExpect(jsonPath("$.inputTokens").value(120))
                .andExpect(jsonPath("$.outputTokens").value(350));
    }

    @Test
    void chatWithAzureProviderRoutes() throws Exception {
        var response = new AgentResponse(AgentType.DEVOPS, "devops", "Deploy with kubectl",
                ModelProvider.AZURE, "gpt-4o-mini", 80, 200);
        when(orchestratorAgent.process(any(AgentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/jarvis/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Deploy to Kubernetes", "conversationId": "k8s-1", "provider": "AZURE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routedTo").value("DEVOPS"))
                .andExpect(jsonPath("$.provider").value("AZURE"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"));
    }

    @Test
    void chatWithoutConversationIdAccepted() throws Exception {
        var response = new AgentResponse(AgentType.FRONTEND, "frontend", "Use flexbox",
                ModelProvider.ANTHROPIC, "claude-sonnet-4-6", 50, 150);
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
                .thenReturn(Flux.just(
                        "[META] {\"routedTo\":\"DEVELOPER\",\"reasoning\":\"code\",\"provider\":\"ANTHROPIC\",\"model\":\"claude-sonnet-4-6\"}",
                        "Hello", " there", " Jarvis",
                        "[USAGE] {\"inputTokens\":50,\"outputTokens\":10}"));

        mockMvc.perform(post("/api/jarvis/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Hello", "conversationId": "stream-1"}
                                """))
                .andExpect(status().isOk());
    }
}
