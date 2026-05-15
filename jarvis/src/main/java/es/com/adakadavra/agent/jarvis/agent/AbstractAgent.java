package es.com.adakadavra.agent.jarvis.agent;

import es.com.adakadavra.agent.jarvis.config.AgentPromptCatalog;
import es.com.adakadavra.agent.jarvis.config.ChatClientFactory;
import es.com.adakadavra.agent.jarvis.google.GoogleWorkspaceContextService;
import es.com.adakadavra.agent.jarvis.model.AgentExecutionResult;
import es.com.adakadavra.agent.jarvis.model.GuardDecision;
import es.com.adakadavra.agent.jarvis.model.AgentType;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import es.com.adakadavra.agent.jarvis.model.TokenMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Base class for all Jarvis agents with dynamic context-aware prompt building.
 * Provides common functionality for building enriched system prompts using StringBuilder
 * to maintain conversation context and extract relevant topics.
 */
public abstract class AbstractAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(AbstractAgent.class);
    private static final int CLI_RECENT_MESSAGES = 2;
    private static final int CLI_SUMMARY_MAX_CHARS = 1400;
    private static final int CLI_MESSAGE_SNIPPET_MAX_CHARS = 220;

    protected final ChatClientFactory chatClientFactory;
    protected final ChatMemory chatMemory;
    protected final List<ToolCallbackProvider> mcpTools;
    protected final AgentPromptCatalog promptCatalog;
    protected final AgentType agentType;
    protected final GoogleWorkspaceContextService googleWorkspaceContextService;
    private final Map<String, SummaryState> cliSummaryByConversation = new ConcurrentHashMap<>();

    protected AbstractAgent(
            ChatClientFactory chatClientFactory,
            ChatMemory chatMemory,
            List<ToolCallbackProvider> mcpTools,
            AgentPromptCatalog promptCatalog,
            GoogleWorkspaceContextService googleWorkspaceContextService,
            AgentType agentType) {
        this.chatClientFactory = chatClientFactory;
        this.chatMemory = chatMemory;
        this.mcpTools = mcpTools != null ? mcpTools : List.of();
        this.promptCatalog = promptCatalog;
        this.googleWorkspaceContextService = googleWorkspaceContextService;
        this.agentType = agentType;
    }

    @Override
    public String process(String request, String conversationId, ModelProvider provider, String requestedModel) {
        return processWithMetadataReactive(request, conversationId, provider, requestedModel)
                .map(AgentExecutionResult::response)
                .block();
    }

    @Override
    public Mono<AgentExecutionResult> processWithMetadataReactive(
            String request,
            String conversationId,
            ModelProvider provider,
            String requestedModel) {
        // Save user message
        var messages = new ArrayList<>(chatMemory.get(conversationId));
        messages.add(new UserMessage(request));
        chatMemory.add(conversationId, messages);

        GuardDecision guardDecision = evaluateGuard(request, provider);
        if (!guardDecision.allowed()) {
            AgentExecutionResult blocked = new AgentExecutionResult(
                    buildGuardRejectionMessage(guardDecision),
                    "guard-blocked");
            messages = new ArrayList<>(chatMemory.get(conversationId));
            messages.add(new AssistantMessage(blocked.response()));
            chatMemory.add(conversationId, messages);
            return Mono.just(blocked);
        }

        AgentExecutionResult executionResult;
        if (chatClientFactory.usesClaudeCli(provider)) {
            executionResult = chatClientFactory.executeWithClaudeCli(
                    buildSystemPromptWithContext(chatMemory.get(conversationId), conversationId, provider),
                    request,
                    requestedModel);
        } else if (chatClientFactory.usesCopilotCli(provider)) {
            executionResult = chatClientFactory.executeWithCopilotCli(
                    buildSystemPromptWithContext(chatMemory.get(conversationId), conversationId, provider),
                    request,
                    requestedModel);
        } else {
            String modelUsed = resolveModelUsed(provider, false, requestedModel);
            String response;
            TokenMetadata tokens = null;
            try {
                var chatResponse = buildAndExecutePrompt(request, conversationId, provider, requestedModel, false).call();
                response = chatResponse.content();
            } catch (RuntimeException ex) {
                if (shouldFallbackToOllama(provider, ex)) {
                    logger.warn("[{}] Primary Ollama model failed. Retrying with fallback model.", type());
                    var chatResponse = buildAndExecutePrompt(request, conversationId, provider, requestedModel, true).call();
                    response = chatResponse.content();
                    modelUsed = resolveModelUsed(provider, true, requestedModel);
                } else {
                    throw ex;
                }
            }
            executionResult = new AgentExecutionResult(response, modelUsed, tokens);
        }

        // Save assistant response
        messages = new ArrayList<>(chatMemory.get(conversationId));
        messages.add(new AssistantMessage(executionResult.response()));
        chatMemory.add(conversationId, messages);

        return Mono.just(executionResult);
    }

    @Override
    public Flux<String> stream(String request, String conversationId, ModelProvider provider, String requestedModel) {
        // Save user message
        var messages = new ArrayList<>(chatMemory.get(conversationId));
        messages.add(new UserMessage(request));
        chatMemory.add(conversationId, messages);

        GuardDecision guardDecision = evaluateGuard(request, provider);
        if (!guardDecision.allowed()) {
            String rejection = buildGuardRejectionMessage(guardDecision);
            messages = new ArrayList<>(chatMemory.get(conversationId));
            messages.add(new AssistantMessage(rejection));
            chatMemory.add(conversationId, messages);
            return Flux.just(rejection);
        }

        if (chatClientFactory.usesClaudeCli(provider)) {
            return chatClientFactory.streamWithClaudeCli(
                    buildSystemPromptWithContext(chatMemory.get(conversationId), conversationId, provider),
                    request,
                    requestedModel);
        }

        if (chatClientFactory.usesCopilotCli(provider)) {
            return chatClientFactory.streamWithCopilotCli(
                    buildSystemPromptWithContext(chatMemory.get(conversationId), conversationId, provider),
                    request,
                    requestedModel);
        }

        // Get streaming response with enriched context
        return buildAndExecutePrompt(request, conversationId, provider, requestedModel, false)
                .stream()
                .content()
                .onErrorResume(ex -> {
                    if (shouldFallbackToOllama(provider, ex)) {
                        logger.warn("[{}] Primary Ollama streaming failed. Retrying with fallback model.", type());
                        return buildAndExecutePrompt(request, conversationId, provider, requestedModel, true)
                                .stream()
                                .content();
                    }
                    return Flux.error(ex);
                });
    }

    /**
     * Builds the prompt with enriched context and executes it.
     */
    private ChatClient.ChatClientRequestSpec buildAndExecutePrompt(
            String request,
            String conversationId,
            ModelProvider provider,
            String requestedModel,
            boolean useFallbackModel) {

        ModelProvider resolvedProvider = provider != null ? provider : chatClientFactory.defaultProvider();
        logger.info("[{}] Procesando petición con proveedor: {}{}",
                type(),
                resolvedProvider,
                useFallbackModel ? " (fallback model)" : "");

        List<Message> history = chatMemory.get(conversationId);
        String contextualSystemPrompt = buildSystemPromptWithContext(history, conversationId, resolvedProvider);

        var prompt = (useFallbackModel
                ? chatClientFactory.agentFallbackClient(resolvedProvider, requestedModel)
                : chatClientFactory.agentClient(resolvedProvider, requestedModel))
                .prompt()
                .system(contextualSystemPrompt)
                .user(request);

        if (!mcpTools.isEmpty()) {
            prompt = prompt.toolCallbacks(mcpTools.toArray(new ToolCallbackProvider[0]));
        }

        return prompt;
    }

    private String resolveModelUsed(ModelProvider provider, boolean fallback, String requestedModel) {
        return chatClientFactory.resolveAgentModelName(provider, fallback, requestedModel);
    }

    private GuardDecision evaluateGuard(String request, ModelProvider provider) {
        String systemPrompt = buildGuardSystemPrompt();
        String rawDecision;

        try {
            ModelProvider resolvedProvider = provider != null ? provider : chatClientFactory.defaultProvider();
            if (chatClientFactory.usesClaudeCli(resolvedProvider)) {
                rawDecision = chatClientFactory.evaluateGuardWithClaudeCli(systemPrompt, request);
            } else if (chatClientFactory.usesCopilotCli(resolvedProvider)) {
                rawDecision = chatClientFactory.evaluateGuardWithCopilotCli(systemPrompt, request, null);
            } else {
                rawDecision = chatClientFactory.orchestratorClient(resolvedProvider)
                        .prompt()
                        .system(systemPrompt)
                        .user(request)
                        .call()
                        .content();
            }
        } catch (Exception ex) {
            logger.warn("[{}] Guard evaluation failed, allowing request. Cause: {}", type(), ex.getMessage());
            return new GuardDecision(true, "Guard unavailable");
        }

        return parseGuardDecision(rawDecision);
    }

    private String buildGuardSystemPrompt() {
        String fallbackGuard = """
                You are a strict scope guard for the %s agent.
                Decide if the user request is within this agent scope.
                Return exactly one line:
                ALLOW|<short reason>
                or
                DENY|<short reason>
                Use DENY when the request should be handled by a different specialist.
                """.formatted(type().name());
        return promptCatalog.guardPrompt(agentType, fallbackGuard);
    }

    private GuardDecision parseGuardDecision(String rawDecision) {
        if (rawDecision == null || rawDecision.isBlank()) {
            return new GuardDecision(true, "Empty guard response");
        }

        String normalized = rawDecision.strip();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ALLOW")) {
            return new GuardDecision(true, extractGuardReason(normalized));
        }
        if (upper.startsWith("DENY")) {
            return new GuardDecision(false, extractGuardReason(normalized));
        }

        // Do not block if the provider returned a non-structured answer.
        return new GuardDecision(true, "Unstructured guard response");
    }

    private String extractGuardReason(String decision) {
        int separator = decision.indexOf('|');
        if (separator < 0 || separator + 1 >= decision.length()) {
            return "No reason provided";
        }
        return decision.substring(separator + 1).trim();
    }

    private String buildGuardRejectionMessage(GuardDecision decision) {
        String reason = decision.reason() == null || decision.reason().isBlank()
                ? "The request is outside this agent scope"
                : decision.reason();
        return "I am the " + type().name() + " specialist. I can only answer requests in my scope. "
                + "Please ask Jarvis to route this to the appropriate agent. "
                + "Reason: " + reason;
    }

    private boolean shouldFallbackToOllama(ModelProvider provider, Throwable ex) {
        ModelProvider resolved = provider != null ? provider : chatClientFactory.defaultProvider();
        if (resolved != ModelProvider.OLLAMA || !chatClientFactory.hasFallbackAgentClient(resolved)) {
            return false;
        }

        String errorChain = buildErrorChain(ex).toLowerCase();
        return errorChain.contains("readtimeoutexception")
                || errorChain.contains("resourceaccessexception")
                || errorChain.contains("timed out")
                || errorChain.contains("i/o error");
    }

    private String buildErrorChain(Throwable error) {
        StringBuilder chain = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            chain.append(current.getClass().getName()).append(':');
            if (current.getMessage() != null) {
                chain.append(current.getMessage());
            }
            chain.append('\n');
            current = current.getCause();
        }
        return chain.toString();
    }

    /**
     * Builds the complete system prompt with dynamic context.
     * This method combines the base system prompt with conversation context.
     */
    private String buildSystemPromptWithContext(List<Message> conversationHistory, String conversationId, ModelProvider provider) {
        StringBuilder prompt = new StringBuilder();

        // Add base system prompt
        prompt.append(promptCatalog.basePrompt(agentType, getBaseSystemPrompt())).append("\n");

        // Add conversation context if available
        if (!conversationHistory.isEmpty()) {
            boolean cliProvider = isCliProvider(provider);
            List<Message> contextualMessages = cliProvider
                    ? extractRecentContext(conversationHistory, CLI_RECENT_MESSAGES)
                    : conversationHistory;

            if (cliProvider) {
                String incrementalSummary = updateCliSummary(conversationId, conversationHistory);
                if (!incrementalSummary.isBlank()) {
                    prompt.append("\n## Conversation Summary:\n");
                    prompt.append(incrementalSummary).append("\n");
                }
            }

            String conversationContext = extractConversationContext(contextualMessages);
            if (!conversationContext.isEmpty()) {
                prompt.append("\n## Contexto de la Conversación:\n");
                prompt.append(conversationContext).append("\n");
            }

            // Add recent topics for better understanding
            List<String> recentTopics = extractRecentTopics(contextualMessages);
            if (!recentTopics.isEmpty()) {
                prompt.append("\n## Temas Recientes:\n");
                recentTopics.forEach(topic -> prompt.append("- ").append(topic).append("\n"));
            }
        }

        // Add agent-specific context enrichment
        String agentSpecificContext = promptCatalog.enrichedAgentContext(agentType, getAgentSpecificContext());
        if (!agentSpecificContext.isEmpty()) {
            prompt.append("\n").append(agentSpecificContext);
        }

        if (googleWorkspaceContextService != null) {
            String googleContext = googleWorkspaceContextService.buildContextSnippet(conversationId);
            if (!googleContext.isBlank()) {
                prompt.append("\n\n## Google Workspace Context:\n").append(googleContext);
            }
        }

        return prompt.toString();
    }

    /**
     * Extracts the last few user and assistant messages as context.
     */
    private String extractConversationContext(List<Message> history) {
        return history.stream()
                .skip(Math.max(0, history.size() - 4))  // Last 4 messages
                .map(this::formatMessage)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private List<Message> extractRecentContext(List<Message> history, int maxMessages) {
        return history.stream()
                .skip(Math.max(0, history.size() - maxMessages))
                .toList();
    }

    /**
     * Extracts relevant topics/keywords from the conversation history.
     */
    private List<String> extractRecentTopics(List<Message> history) {
        return history.stream()
                .filter(UserMessage.class::isInstance)
                .map(this::formatMessage)
                .filter(s -> !s.isEmpty())
                .flatMap(content -> extractKeywords(content).stream())
                .distinct()
                .limit(5)
                .toList();
    }

    /**
     * Formats a message for context extraction.
     */
    private String formatMessage(Message msg) {
        if (msg instanceof UserMessage userMessage) {
            return "User: " + sanitizeMessageText(userMessage.getText());
        }
        if (msg instanceof AssistantMessage assistantMessage) {
            return "Assistant: " + sanitizeMessageText(assistantMessage.getText());
        }
        return sanitizeMessageText(msg.toString());
    }

    private String sanitizeMessageText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private boolean isCliProvider(ModelProvider provider) {
        return chatClientFactory.usesClaudeCli(provider) || chatClientFactory.usesCopilotCli(provider);
    }

    private String updateCliSummary(String conversationId, List<Message> fullHistory) {
        int summaryBoundary = Math.max(0, fullHistory.size() - CLI_RECENT_MESSAGES);
        if (summaryBoundary == 0) {
            cliSummaryByConversation.remove(conversationId);
            return "";
        }

        SummaryState current = cliSummaryByConversation.get(conversationId);
        if (current == null || summaryBoundary < current.processedMessages()) {
            String rebuilt = summarizeMessages(fullHistory.subList(0, summaryBoundary), "");
            SummaryState newState = new SummaryState(summaryBoundary, rebuilt);
            cliSummaryByConversation.put(conversationId, newState);
            return rebuilt;
        }

        if (summaryBoundary == current.processedMessages()) {
            return current.summary();
        }

        String appended = summarizeMessages(
                fullHistory.subList(current.processedMessages(), summaryBoundary),
                current.summary());
        SummaryState updated = new SummaryState(summaryBoundary, appended);
        cliSummaryByConversation.put(conversationId, updated);
        return appended;
    }

    private String summarizeMessages(List<Message> messages, String baseSummary) {
        String summary = baseSummary == null ? "" : baseSummary;
        for (Message message : messages) {
            String formatted = formatMessage(message);
            if (formatted.isBlank()) {
                continue;
            }

            if (formatted.length() > CLI_MESSAGE_SNIPPET_MAX_CHARS) {
                formatted = formatted.substring(0, CLI_MESSAGE_SNIPPET_MAX_CHARS) + "...";
            }

            summary = summary.isBlank() ? formatted : summary + " | " + formatted;
            if (summary.length() > CLI_SUMMARY_MAX_CHARS) {
                summary = summary.substring(summary.length() - CLI_SUMMARY_MAX_CHARS);
            }
        }
        return summary;
    }

    private record SummaryState(int processedMessages, String summary) {
    }

    /**
     * Extracts meaningful keywords from text content.
     */
    private List<String> extractKeywords(String content) {
        return java.util.Arrays.stream(content.split("[\\s,.:;!?()]+"))
                .filter(word -> word.length() > 4)  // Words longer than 4 characters
                .limit(3)
                .toList();
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

    // TODO: Implement token extraction when Spring AI API provides access to ChatResponse metadata
    // This method will be used to extract TokenMetadata from ChatResponse objects
    // Currently, the ChatClient.call() method type resolution needs clarification
}

