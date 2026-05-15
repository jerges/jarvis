package es.com.adakadavra.agent.jarvis.cli.claude;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.com.adakadavra.agent.jarvis.model.RoutingDecision;
import es.com.adakadavra.agent.jarvis.model.TokenMetadata;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

public class ClaudeCliJsonParser {

    private final ObjectMapper objectMapper;

    public ClaudeCliJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public es.com.adakadavra.agent.jarvis.cli.claude.ClaudeCliResponse parseResponse(String jsonPayload, String fallbackModel) {
        try {
            JsonNode root = parsePayload(jsonPayload);
            String content = root.path("result").asText();
            String modelUsed = resolveModelUsed(root, fallbackModel);
            TokenMetadata tokens = extractTokenMetadata(root.path("usage"));
            return new es.com.adakadavra.agent.jarvis.cli.claude.ClaudeCliResponse(content, modelUsed, tokens);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Could not parse Claude CLI JSON response", ex);
        }
    }

    public RoutingDecision parseRoutingDecision(String jsonPayload) {
        try {
            JsonNode root = parsePayload(jsonPayload);
            JsonNode structuredOutput = root.path("structured_output");
            if (!structuredOutput.isMissingNode() && !structuredOutput.isNull()) {
                return objectMapper.treeToValue(structuredOutput, RoutingDecision.class);
            }
            return objectMapper.readValue(root.path("result").asText(), RoutingDecision.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Could not parse Claude CLI routing decision", ex);
        }
    }

    public Optional<String> extractStreamingText(String line) {
        try {
            JsonNode root = objectMapper.readTree(line);
            if (!"stream_event".equals(root.path("type").asText())) {
                return Optional.empty();
            }

            JsonNode event = root.path("event");
            if (!"content_block_delta".equals(event.path("type").asText())) {
                return Optional.empty();
            }

            JsonNode delta = event.path("delta");
            if (!"text_delta".equals(delta.path("type").asText())) {
                return Optional.empty();
            }

            String text = delta.path("text").asText("");
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private String resolveModelUsed(JsonNode root, String fallbackModel) {
        JsonNode modelUsage = root.path("modelUsage");
        if (modelUsage.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = modelUsage.fields();
            Optional<Map.Entry<String, JsonNode>> selectedModel = stream(fields)
                    .max(Comparator.comparingDouble(entry -> entry.getValue().path("costUSD").asDouble(0.0d)));
            if (selectedModel.isPresent()) {
                return selectedModel.get().getKey();
            }
        }
        return fallbackModel;
    }

    private TokenMetadata extractTokenMetadata(JsonNode usageNode) {
        if (usageNode == null || usageNode.isMissingNode() || usageNode.isNull()) {
            return null;
        }

        int inputTokens = usageNode.path("input_tokens").asInt(0)
                + usageNode.path("cache_creation_input_tokens").asInt(0)
                + usageNode.path("cache_read_input_tokens").asInt(0);
        int outputTokens = usageNode.path("output_tokens").asInt(0);
        int totalTokens = inputTokens + outputTokens;

        if (inputTokens == 0 && outputTokens == 0) {
            return null;
        }

        return new TokenMetadata(inputTokens, outputTokens, totalTokens);
    }

    private <T> java.util.stream.Stream<T> stream(Iterator<T> iterator) {
        Iterable<T> iterable = () -> iterator;
        return java.util.stream.StreamSupport.stream(iterable.spliterator(), false);
    }

    private JsonNode parsePayload(String payload) throws JsonProcessingException {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            String sanitizedPayload = extractJsonObject(payload)
                    .orElseThrow(() -> ex);
            return objectMapper.readTree(sanitizedPayload);
        }
    }

    private Optional<String> extractJsonObject(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        int start = payload.indexOf('{');
        if (start < 0) {
            return Optional.empty();
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = start; index < payload.length(); index++) {
            char current = payload.charAt(index);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (current == '\\') {
                escaped = true;
                continue;
            }

            if (current == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return Optional.of(payload.substring(start, index + 1));
                }
            }
        }

        return Optional.empty();
    }
}


