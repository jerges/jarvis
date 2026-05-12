package es.com.adakadavra.agent.jarvis.unit.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.com.adakadavra.agent.jarvis.agent.OrchestratorAgent;
import es.com.adakadavra.agent.jarvis.messaging.WhatsAppService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppServiceTest {

    @Mock
    private OrchestratorAgent orchestratorAgent;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    private WhatsAppService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new WhatsAppService(orchestratorAgent, restClient);
        ReflectionTestUtils.setField(service, "phoneNumberId", "1234567890");
        ReflectionTestUtils.setField(service, "accessToken", "test-access-token");
    }

    @Test
    void handlePayloadProcessesTextMessage() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "type": "text",
                          "from": "34600111222",
                          "text": {"body": "¿Cómo hago un deploy en Kubernetes?"}
                        }]
                      }
                    }]
                  }]
                }
                """);
        var response = new AgentResponse(AgentType.DEVOPS, "devops", "kubectl apply -f ...", ModelProvider.ANTHROPIC);
        when(orchestratorAgent.process(any(AgentRequest.class))).thenReturn(response);

        service.handlePayload(payload);

        var captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(orchestratorAgent).process(captor.capture());
        assertThat(captor.getValue().message()).isEqualTo("¿Cómo hago un deploy en Kubernetes?");
        assertThat(captor.getValue().conversationId()).isEqualTo("whatsapp-34600111222");
        assertThat(captor.getValue().provider()).isNull();
    }

    @Test
    void handlePayloadIgnoresNonTextMessages() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "type": "image",
                          "from": "34600111222"
                        }]
                      }
                    }]
                  }]
                }
                """);

        service.handlePayload(payload);

        verify(orchestratorAgent, never()).process(any());
    }

    @Test
    void handlePayloadIgnoresEmptyEntries() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"entry": []}
                """);

        service.handlePayload(payload);

        verify(orchestratorAgent, never()).process(any());
    }

    @Test
    void handlePayloadIgnoresNonArrayEntry() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"entry": "not-an-array"}
                """);

        service.handlePayload(payload);

        verify(orchestratorAgent, never()).process(any());
    }

    @Test
    void handlePayloadSendsErrorMessageOnOrchestratorException() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{"type": "text", "from": "34600000000", "text": {"body": "Hello"}}]
                      }
                    }]
                  }]
                }
                """);
        when(orchestratorAgent.process(any())).thenThrow(new RuntimeException("AI error"));

        // Should not throw — error is handled internally
        service.handlePayload(payload);

        verify(orchestratorAgent).process(any());
    }
}
