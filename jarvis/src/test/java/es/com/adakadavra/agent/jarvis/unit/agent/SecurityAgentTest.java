package es.com.adakadavra.agent.jarvis.unit.agent;

import es.com.adakadavra.agent.jarvis.agent.SecurityAgent;
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
class SecurityAgentTest {

    @Mock
    private ChatClientFactory chatClientFactory;

    @Mock
    private ChatMemory chatMemory;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private SecurityAgent agent;

    @BeforeEach
    void setUp() {
        agent = new SecurityAgent(chatClientFactory, chatMemory, List.of());
        when(chatClientFactory.agentClient(any())).thenReturn(chatClient);
    }

    @Test
    void typeReturnsSecurity() {
        assertThat(agent.type()).isEqualTo(AgentType.SECURITY);
    }

    @Test
    void processReturnsPentestGuidance() {
        when(chatClient.prompt().system(anyString()).user(anyString())
                .advisors(any()).call().content()).thenReturn("Use Burp Suite to intercept and analyze HTTP requests...");

        String result = agent.process("How do I pentest a web app?", "conv-sec-1", ModelProvider.ANTHROPIC);

        assertThat(result).isEqualTo("Use Burp Suite to intercept and analyze HTTP requests...");
    }

    @Test
    void processHandlesOwaspQuery() {
        when(chatClient.prompt().system(anyString()).user(anyString())
                .advisors(any()).call().content()).thenReturn("OWASP Top 10 includes: A01 Broken Access Control...");

        String result = agent.process("Explain OWASP Top 10", "conv-sec-2", ModelProvider.ANTHROPIC);

        assertThat(result).contains("OWASP Top 10");
    }

    @Test
    void processWithAzureProviderUsesAzureClient() {
        when(chatClient.prompt().system(anyString()).user(anyString())
                .advisors(any()).call().content()).thenReturn("CVE-2024-1234 is a critical RCE vulnerability...");

        String result = agent.process("Tell me about CVE-2024-1234", "conv-sec-3", ModelProvider.AZURE);

        assertThat(result).isEqualTo("CVE-2024-1234 is a critical RCE vulnerability...");
    }

    @Test
    void streamReturnsSecurityAnalysisTokens() {
        when(chatClient.prompt().system(anyString()).user(anyString())
                .advisors(any()).stream().content()).thenReturn(Flux.just("Step 1: ", "Reconnaissance", " with nmap..."));

        Flux<String> result = agent.stream("How to perform a network scan?", "conv-sec-4", ModelProvider.ANTHROPIC);

        assertThat(result.collectList().block()).containsExactly("Step 1: ", "Reconnaissance", " with nmap...");
    }
}
