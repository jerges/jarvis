package es.com.adakadavra.agent.jarvis.unit.agent;

import es.com.adakadavra.agent.jarvis.agent.Agent;
import es.com.adakadavra.agent.jarvis.agent.OrchestratorAgent;
import es.com.adakadavra.agent.jarvis.config.ChatClientFactory;
import es.com.adakadavra.agent.jarvis.model.AgentRequest;
import es.com.adakadavra.agent.jarvis.model.AgentResponse;
import es.com.adakadavra.agent.jarvis.model.AgentType;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import es.com.adakadavra.agent.jarvis.model.RoutingDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestratorAgentTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient orchestratorChatClient;

    @Mock
    private ChatClientFactory chatClientFactory;

    @Mock
    private Agent developerAgent;

    @Mock
    private Agent devopsAgent;

    private OrchestratorAgent orchestrator;

    @BeforeEach
    void setUp() {
        when(developerAgent.type()).thenReturn(AgentType.DEVELOPER);
        when(devopsAgent.type()).thenReturn(AgentType.DEVOPS);
        orchestrator = new OrchestratorAgent(chatClientFactory, List.of(developerAgent, devopsAgent));
    }

    @Test
    void processRoutesToCorrectAgent() {
        var decision = new RoutingDecision(AgentType.DEVELOPER, "developer question");
        stubRoutingDecision(null, decision);
        when(chatClientFactory.defaultProvider()).thenReturn(ModelProvider.ANTHROPIC);
        when(chatClientFactory.getAgentModelName(ModelProvider.ANTHROPIC)).thenReturn("claude-sonnet-4-6");
        when(developerAgent.processWithUsage(anyString(), anyString(), any()))
                .thenReturn(new Agent.AgentResult("Java response", 100, 50));

        AgentResponse result = orchestrator.process(new AgentRequest("How do I write a Java stream?", "conv-1", null));

        assertThat(result.routedTo()).isEqualTo(AgentType.DEVELOPER);
        assertThat(result.reasoning()).isEqualTo("developer question");
        assertThat(result.response()).isEqualTo("Java response");
        assertThat(result.provider()).isEqualTo(ModelProvider.ANTHROPIC);
        assertThat(result.model()).isEqualTo("claude-sonnet-4-6");
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.outputTokens()).isEqualTo(50);
    }

    @Test
    void processGeneratesUuidWhenConversationIdIsNull() {
        var decision = new RoutingDecision(AgentType.DEVELOPER, "reason");
        stubRoutingDecision(null, decision);
        when(chatClientFactory.defaultProvider()).thenReturn(ModelProvider.ANTHROPIC);
        when(chatClientFactory.getAgentModelName(any())).thenReturn("claude-sonnet-4-6");
        when(developerAgent.processWithUsage(anyString(), anyString(), any()))
                .thenReturn(new Agent.AgentResult("response", 0, 0));

        orchestrator.process(new AgentRequest("message", null, null));

        var captor = ArgumentCaptor.forClass(String.class);
        verify(developerAgent).processWithUsage(anyString(), captor.capture(), any());
        assertThat(captor.getValue()).isNotBlank();
    }

    @Test
    void processUsesProvidedConversationId() {
        var decision = new RoutingDecision(AgentType.DEVELOPER, "reason");
        stubRoutingDecision(null, decision);
        when(chatClientFactory.defaultProvider()).thenReturn(ModelProvider.ANTHROPIC);
        when(chatClientFactory.getAgentModelName(any())).thenReturn("claude-sonnet-4-6");
        when(developerAgent.processWithUsage(anyString(), anyString(), any()))
                .thenReturn(new Agent.AgentResult("response", 0, 0));

        orchestrator.process(new AgentRequest("message", "my-session-id", null));

        var captor = ArgumentCaptor.forClass(String.class);
        verify(developerAgent).processWithUsage(anyString(), captor.capture(), any());
        assertThat(captor.getValue()).isEqualTo("my-session-id");
    }

    @Test
    void processResolvesProviderFromRequestWhenProvided() {
        var decision = new RoutingDecision(AgentType.DEVELOPER, "reason");
        stubRoutingDecision(ModelProvider.AZURE, decision);
        when(chatClientFactory.getAgentModelName(ModelProvider.AZURE)).thenReturn("gpt-4o-mini");
        when(developerAgent.processWithUsage(anyString(), anyString(), any()))
                .thenReturn(new Agent.AgentResult("response", 0, 0));

        AgentResponse result = orchestrator.process(new AgentRequest("message", "conv", ModelProvider.AZURE));

        assertThat(result.provider()).isEqualTo(ModelProvider.AZURE);
        assertThat(result.model()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void processUsesDefaultProviderWhenRequestProviderIsNull() {
        var decision = new RoutingDecision(AgentType.DEVOPS, "devops");
        stubRoutingDecision(null, decision);
        when(chatClientFactory.defaultProvider()).thenReturn(ModelProvider.ANTHROPIC);
        when(chatClientFactory.getAgentModelName(ModelProvider.ANTHROPIC)).thenReturn("claude-sonnet-4-6");
        when(devopsAgent.processWithUsage(anyString(), anyString(), any()))
                .thenReturn(new Agent.AgentResult("k8s answer", 80, 200));

        AgentResponse result = orchestrator.process(new AgentRequest("How do I deploy to Kubernetes?", "conv", null));

        assertThat(result.provider()).isEqualTo(ModelProvider.ANTHROPIC);
    }

    @Test
    void streamDelegatesToCorrectAgentWithMetaPrefix() {
        var decision = new RoutingDecision(AgentType.DEVOPS, "infra question");
        stubRoutingDecision(ModelProvider.ANTHROPIC, decision);
        when(chatClientFactory.defaultProvider()).thenReturn(ModelProvider.ANTHROPIC);
        when(chatClientFactory.getAgentModelName(ModelProvider.ANTHROPIC)).thenReturn("claude-sonnet-4-6");
        when(devopsAgent.stream(anyString(), anyString(), any())).thenReturn(Flux.just("token1", "token2"));

        Flux<String> result = orchestrator.stream(new AgentRequest("Deploy to k8s", "conv", ModelProvider.ANTHROPIC));
        List<String> tokens = result.collectList().block();

        assertThat(tokens).hasSize(3);
        assertThat(tokens.get(0)).startsWith("[META]");
        assertThat(tokens.get(0)).contains("DEVOPS");
        assertThat(tokens.get(0)).contains("claude-sonnet-4-6");
        assertThat(tokens.subList(1, 3)).containsExactly("token1", "token2");
    }

    private void stubRoutingDecision(ModelProvider provider, RoutingDecision decision) {
        when(chatClientFactory.orchestratorClient(provider)).thenReturn(orchestratorChatClient);
        when(orchestratorChatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .entity(RoutingDecision.class)).thenReturn(decision);
    }
}
