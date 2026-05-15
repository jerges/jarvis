package es.com.adakadavra.agent.jarvis.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OllamaModelService {

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final List<String> fallbackModels;
    private final Duration cacheTtl;
    private final AtomicReference<ModelCache> cache = new AtomicReference<>();

    public OllamaModelService(
            ObjectMapper objectMapper,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${jarvis.ollama.orchestrator-model:gemma4:latest}") String orchestratorModel,
            @Value("${jarvis.ollama.agent-model:qwen3-coder:30b}") String agentModel,
            @Value("${jarvis.ollama.fallback-agent-model:gemma4:latest}") String fallbackAgentModel,
            @Value("${jarvis.ollama.models-cache-seconds:60}") long cacheSeconds) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.fallbackModels = distinctNonBlank(orchestratorModel, agentModel, fallbackAgentModel);
        this.cacheTtl = Duration.ofSeconds(Math.max(0, cacheSeconds));
    }

    public List<String> listModels() {
        ModelCache cached = cache.get();
        long now = System.currentTimeMillis();
        if (cached != null && now < cached.expiresAtMillis()) {
            return cached.models();
        }

        List<String> models = loadModelsFromOllama();
        cache.set(new ModelCache(models, now + cacheTtl.toMillis()));
        return models;
    }

    private List<String> loadModelsFromOllama() {
        try {
            String payload = RestClient.builder()
                    .baseUrl(baseUrl)
                    .build()
                    .get()
                    .uri("/api/tags")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            if (payload == null || payload.isBlank()) {
                return fallbackModels;
            }

            JsonNode root = objectMapper.readTree(payload);
            JsonNode modelsNode = root.path("models");
            if (!modelsNode.isArray()) {
                return fallbackModels;
            }

            Set<String> models = new LinkedHashSet<>();
            for (JsonNode modelNode : modelsNode) {
                String modelName = modelNode.path("name").asText("").trim();
                if (!modelName.isBlank()) {
                    models.add(modelName);
                }
            }

            if (models.isEmpty()) {
                models.addAll(fallbackModels);
            }
            return List.copyOf(models);
        } catch (Exception ex) {
            return fallbackModels;
        }
    }

    private List<String> distinctNonBlank(String... values) {
        Set<String> items = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                items.add(value.trim());
            }
        }
        if (items.isEmpty()) {
            items.add("gemma4:latest");
        }
        return new ArrayList<>(items);
    }

    private record ModelCache(List<String> models, long expiresAtMillis) {
    }
}

