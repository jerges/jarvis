package es.com.adakadavra.agent.jarvis.unit.agent;

import es.com.adakadavra.agent.jarvis.agent.DeveloperAgent;
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
class DeveloperAgentTest {

    @Mock
    private ChatClientFactory chatClientFactory;

    @Mock
    private ChatMemory chatMemory;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private DeveloperAgent agent;

    @BeforeEach
    void setUp() {
        agent = new DeveloperAgent(chatClientFactory, chatMemory, List.of());
        when(chatClientFactory.agentClient(any())).thenReturn(chatClient);
    }

    @Test
    void typeReturnsDeveloper() {
        assertThat(agent.type()).isEqualTo(AgentType.DEVELOPER);
    }

    @Test
    void processReturnsContentFromAi() {
        when(chatClient.prompt().system(anyString()).user(anyString())
                .advisors(any()).call().content()).thenReturn("Here is how to do it in Java...");

        String result = agent.process("How do streams work in Java?", "conv-1", ModelProvider.ANTHROPIC);

        assertThat(result).isEqualTo("Here is how to do it in Java...");
    }

    @Test
    void processWithAzureProviderUsesAzureClient() {
        when(chatClient.prompt().system(anyString()).user(anyString())
                .advisors(any()).call().content()).thenReturn("Azure response");

        String result = agent.process("Explain SOLID principles", "conv-2", ModelProvider.AZURE);

        assertThat(result).isEqualTo("Azure response");
    }

    @Test
    void streamReturnsFluxFromAi() {
        when(chatClient.prompt().system(anyString()).user(anyString())
                .advisors(any()).stream().content()).thenReturn(Flux.just("token1", " token2", " token3"));

        Flux<String> result = agent.stream("Show me a design pattern", "conv-3", ModelProvider.ANTHROPIC);

        assertThat(result.collectList().block()).containsExactly("token1", " token2", " token3");
    }
}
