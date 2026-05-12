package es.com.adakadavra.agent.jarvis.unit.api;

import es.com.adakadavra.agent.jarvis.api.CopilotCliController;
import es.com.adakadavra.agent.jarvis.copilot.CopilotCliService;
import es.com.adakadavra.agent.jarvis.model.CopilotRequest;
import es.com.adakadavra.agent.jarvis.model.CopilotResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CopilotCliController.class)
class CopilotCliControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CopilotCliService copilotService;

    @Test
    void suggestReturns200WithCommand() throws Exception {
        when(copilotService.chat(any(CopilotRequest.class)))
                .thenReturn(Mono.just(new CopilotResponse("docker ps -a", "suggest")));

        mockMvc.perform(post("/api/jarvis/copilot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "list all docker containers", "mode": "suggest", "target": "shell"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("docker ps -a"))
                .andExpect(jsonPath("$.mode").value("suggest"));
    }

    @Test
    void explainReturns200WithExplanation() throws Exception {
        when(copilotService.chat(any(CopilotRequest.class)))
                .thenReturn(Mono.just(new CopilotResponse("docker ps -a lists all containers including stopped ones", "explain")));

        mockMvc.perform(post("/api/jarvis/copilot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "docker ps -a", "mode": "explain"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").exists())
                .andExpect(jsonPath("$.mode").value("explain"));
    }

    @Test
    void streamReturnsServerSentEvents() throws Exception {
        when(copilotService.stream(any(CopilotRequest.class)))
                .thenReturn(Flux.just("docker ps -a\n", "[DONE] {\"type\":\"done\"}"));

        mockMvc.perform(post("/api/jarvis/copilot/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "list containers", "mode": "suggest", "target": "shell"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void gitSuggestAccepted() throws Exception {
        when(copilotService.chat(any(CopilotRequest.class)))
                .thenReturn(Mono.just(new CopilotResponse("git rebase -i HEAD~3", "suggest")));

        mockMvc.perform(post("/api/jarvis/copilot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "squash last 3 commits", "mode": "suggest", "target": "git"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("git rebase -i HEAD~3"));
    }
}
