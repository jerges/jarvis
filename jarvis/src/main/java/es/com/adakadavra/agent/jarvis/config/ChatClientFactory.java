package es.com.adakadavra.agent.jarvis.config;

import es.com.adakadavra.agent.jarvis.model.AgentExecutionResult;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import es.com.adakadavra.agent.jarvis.model.RoutingDecision;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import reactor.core.publisher.Flux;

import java.util.EnumMap;
import java.util.Map;

public class ChatClientFactory {

    private static final String ANTHROPIC_ORCHESTRATOR_MODEL = "claude-opus-4-7";
    private static final String ANTHROPIC_AGENT_MODEL = "claude-sonnet-4-6";

    private final Map<ModelProvider, ChatClient> orchestratorClients = new EnumMap<>(ModelProvider.class);
    private final Map<ModelProvider, ChatClient> agentClients = new EnumMap<>(ModelProvider.class);
    private final ModelProvider defaultProvider;
    private final OllamaChatModel ollamaModel;
    private final ClaudeCliClient claudeCliClient;
    private final String azureAgentDeployment;
    private final String ollamaAgentModel;
    private final String ollamaFallbackAgentModel;
    private final String claudeCliOrchestratorModel;
    private final String claudeCliAgentModel;
    private ChatClient ollamaFallbackAgentClient;

    public ChatClientFactory(
            AnthropicChatModel anthropicModel,
            AzureOpenAiChatModel azureModel,
            OllamaChatModel ollamaModel,
            ClaudeCliClient claudeCliClient,
            ModelProvider defaultProvider,
            String azureOrchestratorDeployment,
            String azureAgentDeployment,
            String ollamaOrchestratorModel,
            String ollamaAgentModel,
            String ollamaFallbackAgentModel,
            String claudeCliOrchestratorModel,
            String claudeCliAgentModel) {

        this.defaultProvider = defaultProvider;
        this.ollamaModel = ollamaModel;
        this.claudeCliClient = claudeCliClient;
        this.azureAgentDeployment = azureAgentDeployment;
        this.ollamaAgentModel = ollamaAgentModel;
        this.ollamaFallbackAgentModel = ollamaFallbackAgentModel;
        this.claudeCliOrchestratorModel = claudeCliOrchestratorModel;
        this.claudeCliAgentModel = claudeCliAgentModel;

        if (anthropicModel != null) {
            orchestratorClients.put(ModelProvider.ANTHROPIC,
                    ChatClient.builder(anthropicModel)
                            .defaultOptions(AnthropicChatOptions.builder()
                                    .model(ANTHROPIC_ORCHESTRATOR_MODEL)
                                    .maxTokens(1024)
                                    .build())
                            .build());

            agentClients.put(ModelProvider.ANTHROPIC,
                    ChatClient.builder(anthropicModel)
                            .defaultOptions(AnthropicChatOptions.builder()
                                    .model(ANTHROPIC_AGENT_MODEL)
                                    .maxTokens(8096)
                                    .build())
                            .build());
        }

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

        if (ollamaModel != null) {
            orchestratorClients.put(ModelProvider.OLLAMA,
                    ChatClient.builder(ollamaModel)
                            .defaultOptions(OllamaChatOptions.builder()
                                    .model(ollamaOrchestratorModel)
                                    .numPredict(1024)
                                    .build())
                            .build());

            agentClients.put(ModelProvider.OLLAMA,
                    ChatClient.builder(ollamaModel)
                            .defaultOptions(OllamaChatOptions.builder()
                                    .model(ollamaAgentModel)
                                    .numPredict(4096)
                                    .build())
                            .build());

            if (ollamaFallbackAgentModel != null
                    && !ollamaFallbackAgentModel.isBlank()
                    && !ollamaFallbackAgentModel.equals(ollamaAgentModel)) {
                ollamaFallbackAgentClient = ChatClient.builder(ollamaModel)
                        .defaultOptions(OllamaChatOptions.builder()
                                .model(ollamaFallbackAgentModel)
                                .numPredict(1024)
                                .build())
                        .build();
            }
        }
    }

    public ChatClient orchestratorClient(ModelProvider provider) {
        ModelProvider resolved = resolve(orchestratorClients, provider);
        ChatClient client = orchestratorClients.get(resolved);
        if (client == null) {
            throw new IllegalStateException(
                    "Provider " + resolved + " not configured. Available providers: " + orchestratorClients.keySet()
                            + ". Check active profile and provider env vars.");
        }
        return client;
    }

    public ChatClient agentClient(ModelProvider provider) {
        return agentClient(provider, null);
    }

    public ChatClient agentClient(ModelProvider provider, String requestedModel) {
        ModelProvider resolved = resolve(agentClients, provider);
        if (resolved == ModelProvider.OLLAMA && requestedModel != null && !requestedModel.isBlank() && ollamaModel != null) {
            return ChatClient.builder(ollamaModel)
                    .defaultOptions(OllamaChatOptions.builder()
                            .model(requestedModel)
                            .numPredict(4096)
                            .build())
                    .build();
        }
        ChatClient client = agentClients.get(resolved);
        if (client == null) {
            throw new IllegalStateException(
                    "Provider " + resolved + " not configured. Available providers: " + agentClients.keySet()
                            + ". Check active profile and provider env vars.");
        }
        return client;
    }

    public ChatClient agentFallbackClient(ModelProvider provider) {
        return agentFallbackClient(provider, null);
    }

    public ChatClient agentFallbackClient(ModelProvider provider, String requestedModel) {
        ModelProvider resolved = resolve(agentClients, provider);
        if (resolved == ModelProvider.OLLAMA && ollamaFallbackAgentClient != null) {
            return ollamaFallbackAgentClient;
        }
        return agentClient(resolved, requestedModel);
    }

    public boolean hasFallbackAgentClient(ModelProvider provider) {
        ModelProvider resolved = resolve(agentClients, provider);
        return resolved == ModelProvider.OLLAMA && ollamaFallbackAgentClient != null;
    }

    public boolean usesClaudeCli(ModelProvider provider) {
        ModelProvider resolved = provider != null ? provider : defaultProvider;
        return resolved == ModelProvider.CLAUDE_CLI;
    }

    public RoutingDecision routeWithClaudeCli(String routingPrompt, String message) {
        return claudeCliClient.route(routingPrompt, message, resolveOrchestratorModelName(ModelProvider.CLAUDE_CLI));
    }

    public AgentExecutionResult executeWithClaudeCli(String systemPrompt, String message, String requestedModel) {
        ClaudeCliResponse response = claudeCliClient.execute(
                systemPrompt,
                message,
                resolveAgentModelName(ModelProvider.CLAUDE_CLI, false, requestedModel));
        return new AgentExecutionResult(response.content(), response.modelUsed(), response.tokens());
    }

    public Flux<String> streamWithClaudeCli(String systemPrompt, String message, String requestedModel) {
        return claudeCliClient.stream(
                systemPrompt,
                message,
                resolveAgentModelName(ModelProvider.CLAUDE_CLI, false, requestedModel));
    }

    public String resolveOrchestratorModelName(ModelProvider provider) {
        ModelProvider resolved = provider != null ? provider : defaultProvider;
        return switch (resolved) {
            case ANTHROPIC -> ANTHROPIC_ORCHESTRATOR_MODEL;
            case CLAUDE_CLI -> claudeCliOrchestratorModel;
            case AZURE -> "gpt-4o";
            case OLLAMA -> ollamaAgentModel;
        };
    }

    public String resolveAgentModelName(ModelProvider provider, boolean fallback, String requestedModel) {
        ModelProvider resolved = provider != null ? provider : defaultProvider;
        return switch (resolved) {
            case ANTHROPIC -> ANTHROPIC_AGENT_MODEL;
            case CLAUDE_CLI -> requestedModel != null && !requestedModel.isBlank() ? requestedModel : claudeCliAgentModel;
            case AZURE -> azureAgentDeployment;
            case OLLAMA -> {
                if (fallback && ollamaFallbackAgentModel != null && !ollamaFallbackAgentModel.isBlank()) {
                    yield ollamaFallbackAgentModel;
                }
                if (!fallback && requestedModel != null && !requestedModel.isBlank()) {
                    yield requestedModel;
                }
                yield ollamaAgentModel;
            }
        };
    }

    public ModelProvider defaultProvider() {
        return defaultProvider;
    }

    public boolean isAvailable(ModelProvider provider) {
        if (provider == ModelProvider.CLAUDE_CLI) {
            return claudeCliClient != null;
        }
        return orchestratorClients.containsKey(provider);
    }

    private ModelProvider resolve(Map<ModelProvider, ChatClient> clients, ModelProvider provider) {
        ModelProvider requested = provider != null ? provider : defaultProvider;
        if (clients.containsKey(requested)) {
            return requested;
        }
        if (provider == null && clients.size() == 1) {
            return clients.keySet().iterator().next();
        }
        return requested;
    }
}
