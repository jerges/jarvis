package es.com.adakadavra.agent.jarvis.config;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean("orchestratorClient")
    public ChatClient orchestratorClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model("claude-opus-4-7")
                        .maxTokens(1024)
                        .build())
                .build();
    }

    @Bean("agentClient")
    public ChatClient agentClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model("claude-sonnet-4-6")
                        .maxTokens(8096)
                        .build())
                .build();
    }
}
