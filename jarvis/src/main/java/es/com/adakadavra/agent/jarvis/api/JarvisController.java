package es.com.adakadavra.agent.jarvis.api;

import es.com.adakadavra.agent.jarvis.agent.JarvisAgent;
import es.com.adakadavra.agent.jarvis.cli.claude.ClaudeCliService;
import es.com.adakadavra.agent.jarvis.cli.copilot.CopilotCliService;
import es.com.adakadavra.agent.jarvis.config.ChatClientFactory;
import es.com.adakadavra.agent.jarvis.config.OllamaModelService;
import es.com.adakadavra.agent.jarvis.model.AgentRequest;
import es.com.adakadavra.agent.jarvis.model.AgentResponse;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jarvis")
public class JarvisController {

    private final JarvisAgent orchestrator;
    private final ChatClientFactory chatClientFactory;
    private final OllamaModelService ollamaModelService;
    private final ClaudeCliService claudeCliService;
    private final CopilotCliService copilotCliService;
    private final String azureAgentDeployment;
    private final String anthropicApiModel;

    public JarvisController(
            JarvisAgent orchestrator,
            ChatClientFactory chatClientFactory,
            OllamaModelService ollamaModelService,
            ClaudeCliService claudeCliService,
            CopilotCliService copilotCliService,
            @org.springframework.beans.factory.annotation.Value("${jarvis.azure.agent-deployment:gpt-35-turbo}") String azureAgentDeployment,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-6}") String anthropicApiModel) {
        this.orchestrator = orchestrator;
        this.chatClientFactory = chatClientFactory;
        this.ollamaModelService = ollamaModelService;
        this.claudeCliService = claudeCliService;
        this.copilotCliService = copilotCliService;
        this.azureAgentDeployment = azureAgentDeployment;
        this.anthropicApiModel = anthropicApiModel;
    }

    @GetMapping("/capabilities")
    public CapabilitiesResponse capabilities() {
        ModelProvider defaultProvider = chatClientFactory.defaultProvider();
        ModelProvider apiProvider = resolveApiProvider(defaultProvider);
        Map<String, ConnectionCapabilities> connections = Map.of(
                "api", new ConnectionCapabilities(apiLabel(apiProvider), apiModels(apiProvider), apiProvider.name(), false, false),
                "ollama", new ConnectionCapabilities("Ollama local", ollamaModelService.listModels(), ModelProvider.OLLAMA.name(), false, false),
                "claude-cli", new ConnectionCapabilities("Claude CLI", claudeCliService.listModels(), ModelProvider.CLAUDE_CLI.name(), true, false),
                "copilot-cli", new ConnectionCapabilities("Copilot CLI", copilotCliService.listModels(), ModelProvider.COPILOT_CLI.name(), true, true)
        );

        return new CapabilitiesResponse(defaultProvider.name(), connections);
    }

    @PostMapping("/chat")
    public Mono<AgentResponse> chat(@RequestBody AgentRequest request) {
        return orchestrator.processReactive(request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody AgentRequest request) {
        return orchestrator.stream(request);
    }

    public record CapabilitiesResponse(String defaultProvider, Map<String, ConnectionCapabilities> connections) {
    }

    public record ConnectionCapabilities(
            String label,
            List<String> models,
            String provider,
            boolean heuristicSupported,
            boolean heuristicRequired) {
    }

    private ModelProvider resolveApiProvider(ModelProvider defaultProvider) {
        if (defaultProvider == ModelProvider.AZURE || defaultProvider == ModelProvider.ANTHROPIC) {
            return defaultProvider;
        }
        if (chatClientFactory.isAvailable(ModelProvider.AZURE)) {
            return ModelProvider.AZURE;
        }
        return ModelProvider.ANTHROPIC;
    }

    private String apiLabel(ModelProvider provider) {
        return provider == ModelProvider.AZURE ? "API (Azure)" : "API (Anthropic)";
    }

    private List<String> apiModels(ModelProvider provider) {
        return provider == ModelProvider.AZURE
                ? List.of(azureAgentDeployment)
                : List.of(anthropicApiModel);
    }
}
