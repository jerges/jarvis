package es.com.adakadavra.agent.jarvis.agent;

import es.com.adakadavra.agent.jarvis.config.ChatClientFactory;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for all Jarvis agents with dynamic context-aware prompt building.
 * Provides common functionality for building enriched system prompts using StringBuilder
 * to maintain conversation context and extract relevant topics.
 */
public abstract class AbstractAgent implements Agent {

    protected final ChatClientFactory chatClientFactory;
    protected final ChatMemory chatMemory;
    protected final List<ToolCallbackProvider> mcpTools;

    protected AbstractAgent(
            ChatClientFactory chatClientFactory,
            ChatMemory chatMemory,
            List<ToolCallbackProvider> mcpTools) {
        this.chatClientFactory = chatClientFactory;
        this.chatMemory = chatMemory;
        this.mcpTools = mcpTools != null ? mcpTools : List.of();
    }

    @Override
    public String process(String request, String conversationId, ModelProvider provider) {
        // Save user message
        var messages = new ArrayList<>(chatMemory.get(conversationId));
        messages.add(new UserMessage(request));
        chatMemory.add(conversationId, messages);

        // Get response with enriched context
        String response = buildAndExecutePrompt(request, conversationId, provider).call().content();

        // Save assistant response
        messages = new ArrayList<>(chatMemory.get(conversationId));
        messages.add(new AssistantMessage(response));
        chatMemory.add(conversationId, messages);

        return response;
    }

    @Override
    public Flux<String> stream(String request, String conversationId, ModelProvider provider) {
        // Save user message
        var messages = new ArrayList<>(chatMemory.get(conversationId));
        messages.add(new UserMessage(request));
        chatMemory.add(conversationId, messages);

        // Get streaming response with enriched context
        return buildAndExecutePrompt(request, conversationId, provider).stream().content();
    }

    /**
     * Builds the prompt with enriched context and executes it.
     */
    private ChatClient.ChatClientRequestSpec buildAndExecutePrompt(
            String request,
            String conversationId,
            ModelProvider provider) {

        List<Message> history = chatMemory.get(conversationId);
        String contextualSystemPrompt = buildSystemPromptWithContext(history);

        var prompt = chatClientFactory.agentClient(provider)
                .prompt()
                .system(contextualSystemPrompt)
                .user(request);

        if (!mcpTools.isEmpty()) {
            prompt = prompt.tools((Object[]) mcpTools.toArray(new ToolCallbackProvider[0]));
        }

        return prompt;
    }

    /**
     * Builds the complete system prompt with dynamic context.
     * This method combines the base system prompt with conversation context.
     */
    private String buildSystemPromptWithContext(List<Message> conversationHistory) {
        StringBuilder prompt = new StringBuilder();

        // Add base system prompt
        prompt.append(getBaseSystemPrompt()).append("\n");

        // Add conversation context if available
        if (!conversationHistory.isEmpty()) {
            String conversationContext = extractConversationContext(conversationHistory);
            if (!conversationContext.isEmpty()) {
                prompt.append("\n## Contexto de la Conversación:\n");
                prompt.append(conversationContext).append("\n");
            }

            // Add recent topics for better understanding
            List<String> recentTopics = extractRecentTopics(conversationHistory);
            if (!recentTopics.isEmpty()) {
                prompt.append("\n## Temas Recientes:\n");
                recentTopics.forEach(topic -> prompt.append("- ").append(topic).append("\n"));
            }
        }

        // Add agent-specific context enrichment
        String agentSpecificContext = getAgentSpecificContext();
        if (!agentSpecificContext.isEmpty()) {
            prompt.append("\n").append(agentSpecificContext);
        }

        return prompt.toString();
    }

    /**
     * Extracts the last few user and assistant messages as context.
     */
    private String extractConversationContext(List<Message> history) {
        return history.stream()
                .skip(Math.max(0, history.size() - 4))  // Last 4 messages
                .map(msg -> formatMessage(msg))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Extracts relevant topics/keywords from the conversation history.
     */
    private List<String> extractRecentTopics(List<Message> history) {
        return history.stream()
                .filter(msg -> msg instanceof UserMessage)
                .map(msg -> formatMessage(msg))
                .filter(s -> !s.isEmpty())
                .flatMap(content -> extractKeywords(content).stream())
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * Formats a message for context extraction.
     */
    private String formatMessage(Message msg) {
        if (msg instanceof UserMessage) {
            return msg.toString();
        } else if (msg instanceof AssistantMessage) {
            return msg.toString();
        }
        return msg.toString();
    }

    /**
     * Extracts meaningful keywords from text content.
     */
    private List<String> extractKeywords(String content) {
        return List.of(content.split("[\\s,.:;!?()]+"))
                .stream()
                .filter(word -> word.length() > 4)  // Words longer than 4 characters
                .limit(3)
                .collect(Collectors.toList());
    }

    /**
     * Returns the base system prompt for this agent.
     * Subclasses must implement this method.
     */
    protected abstract String getBaseSystemPrompt();

    /**
     * Returns agent-specific contextual enrichment.
     * Can be overridden by subclasses for additional context.
     * Default implementation returns empty string.
     */
    protected String getAgentSpecificContext() {
        return "";
    }
}

