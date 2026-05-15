package es.com.adakadavra.agent.jarvis.cli.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Claude CLI client")
class ClaudeCliClientTest {

    private final ClaudeCliClient client = new ClaudeCliClient(
            new ClaudeCliJsonParser(new ObjectMapper()),
            "claude",
            Path.of("."),
            Duration.ofSeconds(5));

    @Test
    @DisplayName("shouldBeDefaultToHaikuWhenModelIsNull")
    void shouldBeDefaultToHaikuWhenModelIsNull() {
        // Arrange + Act
        String resolved = client.resolveModelForCli(null);

        // Assert
        assertThat(resolved).isEqualTo("haiku");
    }

    @Test
    @DisplayName("shouldBeNormalizeAliasModelWhenUppercaseValueIsProvided")
    void shouldBeNormalizeAliasModelWhenUppercaseValueIsProvided() {
        // Arrange + Act
        String resolved = client.resolveModelForCli("  SONNET  ");

        // Assert
        assertThat(resolved).isEqualTo("sonnet");
    }

    @Test
    @DisplayName("shouldBeAcceptExplicitClaudeModelId")
    void shouldBeAcceptExplicitClaudeModelId() {
        // Arrange + Act
        String resolved = client.resolveModelForCli("claude-opus-4-7");

        // Assert
        assertThat(resolved).isEqualTo("claude-opus-4-7");
    }

    @Test
    @DisplayName("shouldBeRejectNonClaudeModel")
    void shouldBeRejectNonClaudeModel() {
        // Arrange + Act + Assert
        assertThatThrownBy(() -> client.resolveModelForCli("gemma4:latest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported Claude CLI model 'gemma4:latest'")
                .hasMessageContaining("Allowed Claude CLI models: haiku, sonnet, opus");
    }
}


