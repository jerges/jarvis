package es.com.adakadavra.agent.jarvis.cli.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.com.adakadavra.agent.jarvis.model.AgentType;
import es.com.adakadavra.agent.jarvis.model.RoutingDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Claude CLI JSON parser")
class ClaudeCliJsonParserTest {

    private final ClaudeCliJsonParser parser = new ClaudeCliJsonParser(new ObjectMapper());

    @Test
    @DisplayName("shouldBeParseCliResponseAndTokenUsage")
    void shouldBeParseCliResponseAndTokenUsage() {
        // Arrange
        String jsonPayload = """
                {
                  "result": "pong",
                  "usage": {
                    "input_tokens": 3,
                    "cache_creation_input_tokens": 10,
                    "cache_read_input_tokens": 5,
                    "output_tokens": 7
                  },
                  "modelUsage": {
                    "claude-haiku-4-5-20251001": {
                      "costUSD": 0.001
                    },
                    "claude-sonnet-4-6": {
                      "costUSD": 0.100
                    }
                  }
                }
                """;

        // Act
        ClaudeCliResponse response = parser.parseResponse(jsonPayload, "sonnet");

        // Assert
        assertThat(response.content()).isEqualTo("pong");
        assertThat(response.modelUsed()).isEqualTo("claude-sonnet-4-6");
        assertThat(response.tokens()).isNotNull();
        assertThat(response.tokens().inputTokens()).isEqualTo(18);
        assertThat(response.tokens().outputTokens()).isEqualTo(7);
        assertThat(response.tokens().totalTokens()).isEqualTo(25);
    }

    @Test
    @DisplayName("shouldBeParseStructuredRoutingDecision")
    void shouldBeParseStructuredRoutingDecision() {
        // Arrange
        String jsonPayload = """
                {
                  "structured_output": {
                    "agentType": "DEVELOPER",
                    "reasoning": "The request is code-related"
                  }
                }
                """;

        // Act
        RoutingDecision decision = parser.parseRoutingDecision(jsonPayload);

        // Assert
        assertThat(decision.agentType()).isEqualTo(AgentType.DEVELOPER);
        assertThat(decision.reasoning()).isEqualTo("The request is code-related");
    }

    @Test
    @DisplayName("shouldBeExtractTextDeltaFromStreamingEvent")
    void shouldBeExtractTextDeltaFromStreamingEvent() {
        // Arrange
        String streamLine = """
                {"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":"hello"}}}
                """;

        // Act
        var delta = parser.extractStreamingText(streamLine);

        // Assert
        assertThat(delta).contains("hello");
    }

    @Test
    @DisplayName("shouldBeParseResponseWhenWarningIsPrependedToJson")
    void shouldBeParseResponseWhenWarningIsPrependedToJson() {
        // Arrange
        String jsonPayload = """
                Warning: no stdin data received in 3s, proceeding without it.
                {"type":"result","result":"ok","usage":{"input_tokens":1,"output_tokens":2}}
                """;

        // Act
        ClaudeCliResponse response = parser.parseResponse(jsonPayload, "sonnet");

        // Assert
        assertThat(response.content()).isEqualTo("ok");
        assertThat(response.tokens()).isNotNull();
        assertThat(response.tokens().inputTokens()).isEqualTo(1);
        assertThat(response.tokens().outputTokens()).isEqualTo(2);
        assertThat(response.tokens().totalTokens()).isEqualTo(3);
    }

    @Test
    @DisplayName("shouldBeParseRoutingDecisionWhenWarningIsPrependedToJson")
    void shouldBeParseRoutingDecisionWhenWarningIsPrependedToJson() {
        // Arrange
        String jsonPayload = """
                Warning: no stdin data received in 3s, proceeding without it.
                {"type":"result","structured_output":{"agentType":"DEVELOPER","reasoning":"Default routing."}}
                """;

        // Act
        RoutingDecision decision = parser.parseRoutingDecision(jsonPayload);

        // Assert
        assertThat(decision.agentType()).isEqualTo(AgentType.DEVELOPER);
        assertThat(decision.reasoning()).isEqualTo("Default routing.");
    }
}


