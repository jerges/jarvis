package es.com.adakadavra.agent.jarvis.agent;

import es.com.adakadavra.agent.jarvis.config.AgentPromptCatalog;
import es.com.adakadavra.agent.jarvis.config.ChatClientFactory;
import es.com.adakadavra.agent.jarvis.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JarvisAgent {

    private static final Logger logger = LoggerFactory.getLogger(JarvisAgent.class);

    private static final String DEFAULT_ROUTING_PROMPT = """
            Eres el orquestador de Jarvis, un sistema multi-agente.
            Tu única tarea es analizar la petición del usuario y decidir qué agente especializado debe manejarla.

            Agentes disponibles (todos con acceso opcional a herramientas GitHub MCP):
            - SECRETARY: agenda, calendario, reuniones, eventos, horarios, recordatorios, documentos, Drive, correos,
              Gmail, actas, tareas, planificación, coordinación, viajes, reservas, asistente personal, secretaría.
              USA ESTE AGENTE para cualquier pregunta sobre el calendario, eventos de hoy, próximas reuniones,
              ficheros en Drive o emails importantes.
            - SOCIAL_MEDIA: redes sociales, WhatsApp, Telegram, Instagram, X, LinkedIn, TikTok, mensajería, bots, comunidades, contenido social
            - DEVELOPER: desarrollo de software, código, arquitectura, APIs, bases de datos, debugging, patrones de diseño,
              backend; puede leer ficheros de repositorios GitHub, buscar código, revisar PRs e issues mediante herramientas MCP.
              USA ESTE AGENTE cuando el usuario pida leer código de un repo, buscar en GitHub, revisar una PR o un issue.
            - DEVOPS: infraestructura, Kubernetes, Docker, CI/CD, cloud (AWS/Azure/GCP), Terraform, monitorización, redes,
              seguridad de infra; puede acceder a repositorios GitHub para revisar workflows, Dockerfiles y configuración de infra.
            - FRONTEND: frontend web, React, Vue, Angular, HTML, CSS, diseño UI/UX, Figma, accesibilidad, rendimiento web;
              puede leer componentes y estilos de repositorios GitHub.
            - SECURITY: ciberseguridad, pentesting, hacking ético, vulnerabilidades, OWASP, CVE, XSS, SQLi, SSRF,
              Burp Suite, Nmap, Metasploit, seguridad en la nube, SIEM, SOC, hardening, forense digital, CTF,
              threat modeling, STRIDE, ISO 27001, PCI-DSS, SOC 2, SAST, DAST, DevSecOps, Red Team, Blue Team;
              puede auditar código de repositorios GitHub en busca de vulnerabilidades.

            Herramientas MCP GitHub disponibles (si el perfil github-mcp está activo):
              get_file_contents, search_code, search_repositories, list_issues, get_issue,
              list_pull_requests, get_pull_request, list_commits, create_issue, create_pull_request,
              fork_repository, create_repository, push_files, create_or_update_file.

            Responde únicamente con el JSON que representa tu decisión de enrutamiento.
            """;

    private static final String DEFAULT_GENERALIST_PROMPT = """
            Eres JARVIS, el coordinador generalista de un sistema multi-agente.
            Si una petición no encaja claramente en un especialista, debes responder tú directamente.

            Reglas:
            - Responde de forma clara, útil y accionable.
            - Si hay ambigüedad, explicita supuestos breves.
            - Si detectas que realmente conviene un especialista, indícalo con una recomendación breve al final.
            - Mantén un tono profesional y colaborativo.
            """;

    private final ChatClientFactory chatClientFactory;
    private final AgentPromptCatalog promptCatalog;
    private final Map<AgentType, Agent> agents;
    private final boolean heuristicRoutingForClaudeCli;
    private final int heuristicMinKeywordHits;
    private final ChatMemory chatMemory;

    public JarvisAgent(
            ChatClientFactory chatClientFactory,
            AgentPromptCatalog promptCatalog,
            List<Agent> agentList,
            @Value("${jarvis.routing.heuristic-for-claude-cli:false}") boolean heuristicRoutingForClaudeCli,
            @Value("${jarvis.routing.heuristic-min-keyword-hits:2}") int heuristicMinKeywordHits,
            ChatMemory chatMemory) {
        this.chatClientFactory = chatClientFactory;
        this.promptCatalog = promptCatalog;
        this.agents = agentList.stream().collect(Collectors.toMap(Agent::type, Function.identity()));
        this.heuristicRoutingForClaudeCli = heuristicRoutingForClaudeCli;
        this.heuristicMinKeywordHits = Math.max(1, heuristicMinKeywordHits);
        this.chatMemory = chatMemory;
    }

    public AgentResponse process(AgentRequest request) {
        return processReactive(request).block();
    }

    public Mono<AgentResponse> processReactive(AgentRequest request) {
        String cid = request.conversationId() != null ? request.conversationId() : UUID.randomUUID().toString();
        ModelProvider provider = request.provider();

        if (shouldHandleInOrchestrator(request.message())) {
            ModelProvider resolved = provider != null ? provider : chatClientFactory.defaultProvider();
            return Mono.just(new AgentResponse(
                    AgentType.JARVIS,
                    "Handled directly by orchestrator self-intent",
                    orchestratorSelfResponse(resolved),
                    resolved,
                    "jarvis-internal",
                    null));
        }

        ModelProvider resolved = provider != null ? provider : chatClientFactory.defaultProvider();

        return routeReactive(request.message(), provider, request.heuristicRoutingEnabled(), request.heuristicMinKeywordHits())
                .flatMap(decision -> {
                    AgentType routedType = resolveRoutedAgentType(decision);
                    String reasoning = decision.reasoning();

                    if (routedType == AgentType.JARVIS) {
                        String response = generateJarvisGeneralistResponse(request.message(), cid, provider, request.model());
                        return Mono.just(new AgentResponse(
                                AgentType.JARVIS,
                                reasoning != null && !reasoning.isBlank()
                                        ? reasoning
                                        : "No specialist fit found. Handled by Jarvis generalist.",
                                response,
                                resolved,
                                chatClientFactory.resolveOrchestratorModelName(provider),
                                null));
                    }

                    return agents.get(routedType)
                            .processWithMetadataReactive(request.message(), cid, provider, request.model())
                            .map(result -> new AgentResponse(routedType, reasoning, result.response(), resolved, result.modelUsed(), result.tokens()));
                });
    }

    public Flux<String> stream(AgentRequest request) {
        String cid = request.conversationId() != null ? request.conversationId() : UUID.randomUUID().toString();
        ModelProvider provider = request.provider();

        if (shouldHandleInOrchestrator(request.message())) {
            ModelProvider resolved = provider != null ? provider : chatClientFactory.defaultProvider();
            return Flux.just(orchestratorSelfResponse(resolved));
        }

        ModelProvider streamResolved = provider != null ? provider : chatClientFactory.defaultProvider();

        return routeReactive(request.message(), provider, request.heuristicRoutingEnabled(), request.heuristicMinKeywordHits())
                .flatMapMany(decision -> {
                    AgentType routedType = resolveRoutedAgentType(decision);
                    String reasoning = decision.reasoning() != null
                            ? decision.reasoning().replace("\"", "'") : "";

                    if (routedType == AgentType.JARVIS) {
                        String response = generateJarvisGeneralistResponse(request.message(), cid, provider, request.model());
                        String metaToken = String.format(
                                "[META] {\"routedTo\":\"%s\",\"reasoning\":\"%s\",\"provider\":\"%s\"}",
                                AgentType.JARVIS.name(),
                                reasoning.isBlank()
                                        ? "No specialist fit found. Handled by Jarvis generalist."
                                        : reasoning,
                                streamResolved.name());
                        return Flux.just(metaToken, response);
                    }

                    String metaToken = String.format(
                            "[META] {\"routedTo\":\"%s\",\"reasoning\":\"%s\",\"provider\":\"%s\"}",
                            routedType.name(), reasoning, streamResolved.name());
                    return Flux.concat(
                            Flux.just(metaToken),
                            agents.get(routedType).stream(request.message(), cid, provider, request.model()));
                });
    }

    private RoutingDecision route(String message, ModelProvider provider, Boolean heuristicRoutingEnabled, Integer heuristicMinKeywordHits) {
        ModelProvider resolved = provider != null ? provider : chatClientFactory.defaultProvider();
        int minKeywordHits = resolveHeuristicMinKeywordHits(heuristicMinKeywordHits);
        logger.info("[JARVIS] Enrutando petición con proveedor: {}", resolved);

        if (chatClientFactory.usesCopilotCli(resolved)) {
            return routeWithHeuristics(message, minKeywordHits);
        }

        if (chatClientFactory.usesClaudeCli(resolved)) {
            if (isHeuristicRoutingEnabledForClaudeCli(heuristicRoutingEnabled)) {
                RoutingDecision heuristicDecision = routeWithHeuristics(message, minKeywordHits);
                if (heuristicDecision.agentType() != AgentType.JARVIS) {
                    return heuristicDecision;
                }
            }
            return chatClientFactory.routeWithClaudeCli(
                    promptCatalog.routingPrompt(DEFAULT_ROUTING_PROMPT),
                    message);
        }

        return chatClientFactory.orchestratorClient(provider)
                .prompt()
                .system(promptCatalog.routingPrompt(DEFAULT_ROUTING_PROMPT))
                .user(message)
                .call()
                .entity(RoutingDecision.class);
    }

    private Mono<RoutingDecision> routeReactive(
            String message,
            ModelProvider provider,
            Boolean heuristicRoutingEnabled,
            Integer heuristicMinKeywordHits) {
        return Mono.fromCallable(() -> route(message, provider, heuristicRoutingEnabled, heuristicMinKeywordHits))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private boolean shouldHandleInOrchestrator(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("who are you")
                || normalized.contains("quien eres")
                || normalized.contains("quién eres")
                || normalized.contains("what can you do")
                || normalized.contains("que puedes hacer")
                || normalized.contains("qué puedes hacer")
                || normalized.contains("how many agents")
                || normalized.contains("cuantos agentes")
                || normalized.contains("cuántos agentes")
                || normalized.contains("list agents")
                || normalized.contains("lista agentes")
                || normalized.contains("qué agentes")
                || normalized.contains("que agentes")
                || normalized.equals("help")
                || normalized.equals("ayuda");
    }

    private String orchestratorSelfResponse(ModelProvider provider) {
        String availableAgents = agents.keySet().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(", "));

        return "Soy JARVIS, el coordinador de este sistema. Coordino y enruto peticiones entre los agentes especializados. "
                + "Actualmente hay " + agents.size() + " agentes disponibles: " + availableAgents + ". "
                + "Proveedor activo: " + provider + ".";
    }

    private AgentType resolveRoutedAgentType(RoutingDecision decision) {
        if (decision != null && decision.agentType() != null && agents.containsKey(decision.agentType())) {
            return decision.agentType();
        }
        return AgentType.JARVIS;
    }

    private RoutingDecision routeWithHeuristics(String message, int minKeywordHits) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);

        HeuristicMatch best = maxByScore(
                new HeuristicMatch(AgentType.SECURITY, "Keyword heuristic: security domain",
                        countMatches(normalized, "owasp", "xss", "sqli", "vulnerab", "cve", "pentest", "seguridad", "security")),
                new HeuristicMatch(AgentType.DEVOPS, "Keyword heuristic: devops/cloud domain",
                        countMatches(normalized, "kubernetes", "docker", "terraform", "ci/cd", "pipeline", "devops", "azure", "aws", "gcp")),
                new HeuristicMatch(AgentType.FRONTEND, "Keyword heuristic: frontend domain",
                        countMatches(normalized, "react", "angular", "vue", "css", "html", "ui", "ux", "frontend")),
                new HeuristicMatch(AgentType.DEVELOPER, "Keyword heuristic: software development domain",
                        countMatches(normalized,
                                "program", "programacion", "programación", "codigo", "código", "code", "java", "spring", "python",
                                "javascript", "typescript", "debug", "bug", "refactor", "api", "backend", "sql", "database")),
                new HeuristicMatch(AgentType.SOCIAL_MEDIA, "Keyword heuristic: social media domain",
                        countMatches(normalized, "instagram", "linkedin", "tiktok", "social", "telegram", "whatsapp", "post")),
                new HeuristicMatch(AgentType.SECRETARY, "Keyword heuristic: secretary domain",
                        countMatches(normalized, "calendario", "agenda", "meeting", "gmail", "drive", "reunion", "secretary"))
        );

        if (best.score() >= minKeywordHits) {
            return new RoutingDecision(best.agentType(), best.reason() + " (score=" + best.score() + ")");
        }

        return new RoutingDecision(
                AgentType.JARVIS,
                "Keyword heuristic: low confidence (score=" + best.score() + ", threshold=" + minKeywordHits + ")");
    }

    private boolean isHeuristicRoutingEnabledForClaudeCli(Boolean requestOverride) {
        return requestOverride != null ? requestOverride : heuristicRoutingForClaudeCli;
    }

    private int resolveHeuristicMinKeywordHits(Integer requestOverride) {
        if (requestOverride == null) {
            return heuristicMinKeywordHits;
        }
        return Math.max(1, requestOverride);
    }

    private String generateJarvisGeneralistResponse(String message, String conversationId, ModelProvider provider, String requestedModel) {
        ModelProvider resolved = provider != null ? provider : chatClientFactory.defaultProvider();
        appendUserMessage(conversationId, message);
        String systemPrompt = buildGeneralistPromptWithContext(conversationId);

        String response;

        if (chatClientFactory.usesClaudeCli(resolved)) {
            response = chatClientFactory.executeWithClaudeCli(systemPrompt, message, requestedModel).response();
        } else if (chatClientFactory.usesCopilotCli(resolved)) {
            response = chatClientFactory.executeWithCopilotCli(systemPrompt, message, requestedModel).response();
        } else {
            response = chatClientFactory.orchestratorClient(resolved)
                    .prompt()
                    .system(systemPrompt)
                    .user(message)
                    .call()
                    .content();
        }

        appendAssistantMessage(conversationId, response);
        return response;
    }

    private String buildGeneralistPromptWithContext(String conversationId) {
        StringBuilder prompt = new StringBuilder(promptCatalog.basePrompt(AgentType.JARVIS, DEFAULT_GENERALIST_PROMPT));
        List<Message> history = chatMemory.get(conversationId);
        if (!history.isEmpty()) {
            String context = history.stream()
                    .skip(Math.max(0, history.size() - 4))
                    .map(this::formatMemoryMessage)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining("\n"));
            if (!context.isBlank()) {
                prompt.append("\n\nConversation context:\n").append(context);
            }
        }
        return prompt.toString();
    }

    private void appendUserMessage(String conversationId, String text) {
        var messages = new java.util.ArrayList<>(chatMemory.get(conversationId));
        messages.add(new UserMessage(text));
        chatMemory.add(conversationId, messages);
    }

    private void appendAssistantMessage(String conversationId, String text) {
        var messages = new java.util.ArrayList<>(chatMemory.get(conversationId));
        messages.add(new AssistantMessage(text));
        chatMemory.add(conversationId, messages);
    }

    private String formatMemoryMessage(Message message) {
        if (message instanceof UserMessage userMessage) {
            return "User: " + userMessage.getText();
        }
        if (message instanceof AssistantMessage assistantMessage) {
            return "Assistant: " + assistantMessage.getText();
        }
        return "";
    }


    private int countMatches(String text, String... keywords) {
        int matches = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                matches++;
            }
        }
        return matches;
    }

    private HeuristicMatch maxByScore(HeuristicMatch... candidates) {
        HeuristicMatch best = candidates[0];
        for (int i = 1; i < candidates.length; i++) {
            if (candidates[i].score() > best.score()) {
                best = candidates[i];
            }
        }
        return best;
    }

    private record HeuristicMatch(AgentType agentType, String reason, int score) {
    }
}

