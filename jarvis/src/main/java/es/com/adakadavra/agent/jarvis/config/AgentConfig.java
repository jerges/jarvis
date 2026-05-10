package es.com.adakadavra.agent.jarvis.config;

import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    public ChatClientFactory chatClientFactory(
            AnthropicChatModel anthropicChatModel,
            @Autowired(required = false) AzureOpenAiChatModel azureChatModel,
            @Value("${jarvis.ai.default-provider:ANTHROPIC}") String defaultProvider,
            @Value("${jarvis.azure.orchestrator-deployment:gpt-4o}") String azureOrchestratorDeployment,
            @Value("${jarvis.azure.agent-deployment:gpt-4o-mini}") String azureAgentDeployment) {

        return new ChatClientFactory(
                anthropicChatModel,
                azureChatModel,
                ModelProvider.valueOf(defaultProvider.toUpperCase()),
                azureOrchestratorDeployment,
                azureAgentDeployment);
    }
}
