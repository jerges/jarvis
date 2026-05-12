package es.com.adakadavra.agent.jarvis.unit.agent;

import es.com.adakadavra.agent.jarvis.agent.SocialMediaAgent;
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
class SocialMediaAgentTest {

    @Mock private ChatClientFactory chatClientFactory;
    @Mock private ChatMemory chatMemory;
    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private SocialMediaAgent agent;

    @BeforeEach
    void setUp() {
        agent = new SocialMediaAgent(chatClientFactory, chatMemory, List.of());
        when(chatClientFactory.agentClient(any())).thenReturn(chatClient);
    }

    @Test
    void typeReturnsSocialMedia() {
        assertThat(agent.type()).isEqualTo(AgentType.SOCIAL_MEDIA);
    }

    @Test
    void processReturnsSocialMediaAnswer() {
        when(chatClient.prompt().system(anyString()).user(anyString())
                .advisors(any()).call().content()).thenReturn("Post consistently and engage with your audience");

        String result = agent.process("How do I grow my Instagram account?", "conv-1", ModelProvider.ANTHROPIC);

        assertThat(result).isEqualTo("Post consistently and engage with your audience");
    }

    @Test
    void streamReturnsFlux() {
        when(chatClient.prompt().system(anyString()).user(anyString())
                .advisors(any()).stream().content()).thenReturn(Flux.just("Create", " engaging", " content"));

        Flux<String> result = agent.stream("Tips for TikTok?", "conv-2", ModelProvider.ANTHROPIC);

        assertThat(result.collectList().block()).containsExactly("Create", " engaging", " content");
    }
}
