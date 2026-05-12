package es.com.adakadavra.agent.jarvis.unit.agent;

import es.com.adakadavra.agent.jarvis.agent.FrontendAgent;
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
class FrontendAgentTest {

    @Mock private ChatClientFactory chatClientFactory;
    @Mock private ChatMemory chatMemory;
    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private FrontendAgent agent;

    @BeforeEach
    void setUp() {
        agent = new FrontendAgent(chatClientFactory, chatMemory, List.of());
        when(chatClientFactory.agentClient(any())).thenReturn(chatClient);
    }

    @Test
    void typeReturnsFrontend() {
        assertThat(agent.type()).isEqualTo(AgentType.FRONTEND);
    }

    @Test
    void processReturnsFrontendAnswer() {
        when(chatClient.prompt().system(anyString()).user(anyString())
                .advisors(any()).call().content()).thenReturn("Use Angular signals for reactive state");

        String result = agent.process("What is the best way to manage state in Angular?", "conv-1", ModelProvider.ANTHROPIC);

        assertThat(result).isEqualTo("Use Angular signals for reactive state");
    }

    @Test
    void streamReturnsFlux() {
        when(chatClient.prompt().system(anyString()).user(anyString())
                .advisors(any()).stream().content()).thenReturn(Flux.just("CSS", " grid", " layout"));

        Flux<String> result = agent.stream("How to center a div?", "conv-2", ModelProvider.ANTHROPIC);

        assertThat(result.collectList().block()).containsExactly("CSS", " grid", " layout");
    }
}
