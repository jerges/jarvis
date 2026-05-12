package es.com.adakadavra.agent.jarvis.unit.config;

import es.com.adakadavra.agent.jarvis.config.ChatClientFactory;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ChatClientFactoryTest {

    @Mock
    private AnthropicChatModel anthropicModel;

    @Mock
    private AzureOpenAiChatModel azureModel;

    @Test
    void onlyAnthropicAvailableWhenAzureIsNull() {
        var factory = new ChatClientFactory(anthropicModel, null, ModelProvider.ANTHROPIC, "gpt-4o", "gpt-4o-mini");

        assertThat(factory.isAvailable(ModelProvider.ANTHROPIC)).isTrue();
        assertThat(factory.isAvailable(ModelProvider.AZURE)).isFalse();
    }

    @Test
    void bothProvidersAvailableWhenAzureConfigured() {
        var factory = new ChatClientFactory(anthropicModel, azureModel, ModelProvider.ANTHROPIC, "gpt-4o", "gpt-4o-mini");

        assertThat(factory.isAvailable(ModelProvider.ANTHROPIC)).isTrue();
        assertThat(factory.isAvailable(ModelProvider.AZURE)).isTrue();
    }

    @Test
    void orchestratorClientReturnsNonNullForAnthropicProvider() {
        var factory = new ChatClientFactory(anthropicModel, null, ModelProvider.ANTHROPIC, "gpt-4o", "gpt-4o-mini");

        ChatClient client = factory.orchestratorClient(ModelProvider.ANTHROPIC);

        assertThat(client).isNotNull();
    }

    @Test
    void agentClientReturnsNonNullForAnthropicProvider() {
        var factory = new ChatClientFactory(anthropicModel, null, ModelProvider.ANTHROPIC, "gpt-4o", "gpt-4o-mini");

        ChatClient client = factory.agentClient(ModelProvider.ANTHROPIC);

        assertThat(client).isNotNull();
    }

    @Test
    void orchestratorClientFallsBackToDefaultWhenProviderIsNull() {
        var factory = new ChatClientFactory(anthropicModel, null, ModelProvider.ANTHROPIC, "gpt-4o", "gpt-4o-mini");

        ChatClient client = factory.orchestratorClient(null);

        assertThat(client).isNotNull();
    }

    @Test
    void agentClientFallsBackToDefaultWhenProviderIsNull() {
        var factory = new ChatClientFactory(anthropicModel, null, ModelProvider.ANTHROPIC, "gpt-4o", "gpt-4o-mini");

        ChatClient client = factory.agentClient(null);

        assertThat(client).isNotNull();
    }

    @Test
    void orchestratorClientThrowsWhenAzureRequestedButNotConfigured() {
        var factory = new ChatClientFactory(anthropicModel, null, ModelProvider.ANTHROPIC, "gpt-4o", "gpt-4o-mini");

        assertThatThrownBy(() -> factory.orchestratorClient(ModelProvider.AZURE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE");
    }

    @Test
    void agentClientThrowsWhenAzureRequestedButNotConfigured() {
        var factory = new ChatClientFactory(anthropicModel, null, ModelProvider.ANTHROPIC, "gpt-4o", "gpt-4o-mini");

        assertThatThrownBy(() -> factory.agentClient(ModelProvider.AZURE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE");
    }

    @Test
    void defaultProviderReturnsConfiguredValue() {
        var factory = new ChatClientFactory(anthropicModel, null, ModelProvider.ANTHROPIC, "gpt-4o", "gpt-4o-mini");

        assertThat(factory.defaultProvider()).isEqualTo(ModelProvider.ANTHROPIC);
    }

    @Test
    void azureOrchestratorAndAgentClientsAreDistinct() {
        var factory = new ChatClientFactory(anthropicModel, azureModel, ModelProvider.ANTHROPIC, "gpt-4o", "gpt-4o-mini");

        ChatClient orchestrator = factory.orchestratorClient(ModelProvider.AZURE);
        ChatClient agent = factory.agentClient(ModelProvider.AZURE);

        assertThat(orchestrator).isNotSameAs(agent);
    }
}
