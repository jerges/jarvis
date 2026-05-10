package es.com.adakadavra.agent.jarvis.config;

import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;

import java.util.EnumMap;
import java.util.Map;

public class ChatClientFactory {

    private final Map<ModelProvider, ChatClient> orchestratorClients = new EnumMap<>(ModelProvider.class);
    private final Map<ModelProvider, ChatClient> agentClients = new EnumMap<>(ModelProvider.class);
    private final ModelProvider defaultProvider;

    public ChatClientFactory(
            AnthropicChatModel anthropicModel,
            AzureOpenAiChatModel azureModel,
            ModelProvider defaultProvider,
            String azureOrchestratorDeployment,
            String azureAgentDeployment) {

        this.defaultProvider = defaultProvider;

        orchestratorClients.put(ModelProvider.ANTHROPIC,
                ChatClient.builder(anthropicModel)
                        .defaultOptions(AnthropicChatOptions.builder()
                                .model("claude-opus-4-7")
                                .maxTokens(1024)
                                .build())
                        .build());

        agentClients.put(ModelProvider.ANTHROPIC,
                ChatClient.builder(anthropicModel)
                        .defaultOptions(AnthropicChatOptions.builder()
                                .model("claude-sonnet-4-6")
                                .maxTokens(8096)
                                .build())
                        .build());

        if (azureModel != null) {
            orchestratorClients.put(ModelProvider.AZURE,
                    ChatClient.builder(azureModel)
                            .defaultOptions(AzureOpenAiChatOptions.builder()
                                    .deploymentName(azureOrchestratorDeployment)
                                    .maxTokens(1024)
                                    .build())
                            .build());

            agentClients.put(ModelProvider.AZURE,
                    ChatClient.builder(azureModel)
                            .defaultOptions(AzureOpenAiChatOptions.builder()
                                    .deploymentName(azureAgentDeployment)
                                    .maxTokens(4096)
                                    .build())
                            .build());
        }
    }

    public ChatClient orchestratorClient(ModelProvider provider) {
        ModelProvider resolved = resolve(provider);
        ChatClient client = orchestratorClients.get(resolved);
        if (client == null) {
            throw new IllegalStateException(
                    "Provider " + resolved + " not configured. Check spring.ai.azure.openai.* properties.");
        }
        return client;
    }

    public ChatClient agentClient(ModelProvider provider) {
        ModelProvider resolved = resolve(provider);
        ChatClient client = agentClients.get(resolved);
        if (client == null) {
            throw new IllegalStateException(
                    "Provider " + resolved + " not configured. Check spring.ai.azure.openai.* properties.");
        }
        return client;
    }

    public ModelProvider defaultProvider() {
        return defaultProvider;
    }

    public boolean isAvailable(ModelProvider provider) {
        return orchestratorClients.containsKey(provider);
    }

    private ModelProvider resolve(ModelProvider provider) {
        return provider != null ? provider : defaultProvider;
    }
}
