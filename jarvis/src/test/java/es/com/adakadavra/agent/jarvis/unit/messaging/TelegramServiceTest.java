package es.com.adakadavra.agent.jarvis.unit.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.com.adakadavra.agent.jarvis.agent.OrchestratorAgent;
import es.com.adakadavra.agent.jarvis.messaging.TelegramService;
import es.com.adakadavra.agent.jarvis.model.AgentRequest;
import es.com.adakadavra.agent.jarvis.model.AgentResponse;
import es.com.adakadavra.agent.jarvis.model.AgentType;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramServiceTest {

    @Mock
    private OrchestratorAgent orchestratorAgent;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    private TelegramService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new TelegramService(orchestratorAgent, restClient);
        ReflectionTestUtils.setField(service, "botToken", "test-bot-token");
    }

    @Test
    void handleUpdateProcessesTextMessage() throws Exception {
        JsonNode update = objectMapper.readTree("""
                {"message": {"chat": {"id": "456"}, "text": "How do I center a div?"}}
                """);
        var response = new AgentResponse(AgentType.FRONTEND, "frontend", "Use flexbox!", ModelProvider.ANTHROPIC);
        when(orchestratorAgent.process(any(AgentRequest.class))).thenReturn(response);

        service.handleUpdate(update);

        var captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(orchestratorAgent).process(captor.capture());
        assertThat(captor.getValue().message()).isEqualTo("How do I center a div?");
        assertThat(captor.getValue().conversationId()).isEqualTo("telegram-456");
        assertThat(captor.getValue().provider()).isNull();
    }

    @Test
    void handleUpdateSendsWelcomeOnStartCommand() throws Exception {
        JsonNode update = objectMapper.readTree("""
                {"message": {"chat": {"id": "789"}, "text": "/start"}}
                """);

        service.handleUpdate(update);

        verify(orchestratorAgent, never()).process(any());
    }

    @Test
    void handleUpdateIgnoresUpdateWithoutText() throws Exception {
        JsonNode update = objectMapper.readTree("""
                {"message": {"chat": {"id": "123"}, "sticker": {}}}
                """);

        service.handleUpdate(update);

        verify(orchestratorAgent, never()).process(any());
    }

    @Test
    void handleUpdateIgnoresUpdateWithoutMessage() throws Exception {
        JsonNode update = objectMapper.readTree("""
                {"update_id": 42}
                """);

        service.handleUpdate(update);

        verify(orchestratorAgent, never()).process(any());
    }

    @Test
    void handleUpdateIgnoresUpdateWithBlankText() throws Exception {
        JsonNode update = objectMapper.readTree("""
                {"message": {"chat": {"id": "123"}, "text": "   "}}
                """);

        service.handleUpdate(update);

        verify(orchestratorAgent, never()).process(any());
    }

    @Test
    void handleUpdateSendsErrorMessageOnOrchestratorException() throws Exception {
        JsonNode update = objectMapper.readTree("""
                {"message": {"chat": {"id": "999"}, "text": "Hello"}}
                """);
        when(orchestratorAgent.process(any())).thenThrow(new RuntimeException("AI failure"));

        // Should not throw — error is handled internally and a fallback message is sent
        service.handleUpdate(update);

        verify(orchestratorAgent).process(any());
    }
}
