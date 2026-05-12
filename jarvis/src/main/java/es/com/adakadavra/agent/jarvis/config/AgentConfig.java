package es.com.adakadavra.agent.jarvis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class AgentConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Bean
    public ClaudeCliJsonParser claudeCliJsonParser(ObjectMapper objectMapper) {
        return new ClaudeCliJsonParser(objectMapper);
    }

    @Bean
    public ClaudeCliClient claudeCliClient(
            ClaudeCliJsonParser claudeCliJsonParser,
            @Value("${jarvis.claude-cli.command:claude}") String command,
            @Value("${jarvis.claude-cli.working-directory:${user.dir}}") String workingDirectory,
            @Value("${jarvis.claude-cli.timeout-seconds:180}") long timeoutSeconds) {

        return new ClaudeCliClient(
                claudeCliJsonParser,
                command,
                Path.of(workingDirectory),
                Duration.ofSeconds(timeoutSeconds));
    }

    @Bean
    public ChatClientFactory chatClientFactory(
            @Autowired(required = false) AnthropicChatModel anthropicChatModel,
            @Autowired(required = false) AzureOpenAiChatModel azureChatModel,
            @Autowired(required = false) OllamaChatModel ollamaChatModel,
            ClaudeCliClient claudeCliClient,
            @Value("${jarvis.ai.default-provider:ANTHROPIC}") String defaultProvider,
            @Value("${jarvis.azure.orchestrator-deployment:gpt-4o}") String azureOrchestratorDeployment,
            @Value("${jarvis.azure.agent-deployment:gpt-4o-mini}") String azureAgentDeployment,
            @Value("${jarvis.ollama.orchestrator-model:gemma4}") String ollamaOrchestratorModel,
            @Value("${jarvis.ollama.agent-model:qwen3-coder:30b}") String ollamaAgentModel,
            @Value("${jarvis.ollama.fallback-agent-model:gemma4:latest}") String ollamaFallbackAgentModel,
            @Value("${jarvis.claude-cli.orchestrator-model:haiku}") String claudeCliOrchestratorModel,
            @Value("${jarvis.claude-cli.agent-model:haiku}") String claudeCliAgentModel) {

        return new ChatClientFactory(
                anthropicChatModel,
                azureChatModel,
                ollamaChatModel,
                claudeCliClient,
                ModelProvider.valueOf(defaultProvider.trim().toUpperCase()),
                azureOrchestratorDeployment,
                azureAgentDeployment,
                ollamaOrchestratorModel,
                ollamaAgentModel,
                ollamaFallbackAgentModel,
                claudeCliOrchestratorModel,
                claudeCliAgentModel);
    }
}
