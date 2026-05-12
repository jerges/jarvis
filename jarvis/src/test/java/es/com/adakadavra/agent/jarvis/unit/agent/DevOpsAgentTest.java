package es.com.adakadavra.agent.jarvis.unit.agent;

import es.com.adakadavra.agent.jarvis.agent.DevOpsAgent;
import es.com.adakadavra.agent.jarvis.config.ChatClientFactory;
import es.com.adakadavra.agent.jarvis.model.AgentType;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevOpsAgentTest {

    @Mock private ChatClientFactory chatClientFactory;
    @Mock private ChatMemory chatMemory;
    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private DevOpsAgent agent;

    @BeforeEach
    void setUp() {
        agent = new DevOpsAgent(chatClientFactory, chatMemory, List.of());
        when(chatClientFactory.agentClient(any())).thenReturn(chatClient);
    }

    @Test
    void typeReturnsDevops() {
        assertThat(agent.type()).isEqualTo(AgentType.DEVOPS);
    }

    @Test
    void processReturnsInfrastructureAnswer() {
        when(chatClient.prompt().system(anyString()).user(anyString())
                .advisors(any()).call().content()).thenReturn("Use kubectl apply -f deployment.yaml");

        String result = agent.process("How do I deploy to Kubernetes?", "conv-1", ModelProvider.ANTHROPIC);

        assertThat(result).isEqualTo("Use kubectl apply -f deployment.yaml");
    }

    @Test
    void streamReturnsFlux() {
        when(chatClient.prompt().system(anyString()).user(anyString())
                .advisors(any()).stream().content()).thenReturn(Flux.just("docker", " compose", " up"));

        Flux<String> result = agent.stream("Explain Docker Compose", "conv-2", ModelProvider.ANTHROPIC);

        assertThat(result.collectList().block()).containsExactly("docker", " compose", " up");
    }
}
