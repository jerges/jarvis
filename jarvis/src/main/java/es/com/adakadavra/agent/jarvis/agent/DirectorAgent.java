package es.com.adakadavra.agent.jarvis.agent;

import es.com.adakadavra.agent.jarvis.config.AgentPromptCatalog;
import es.com.adakadavra.agent.jarvis.config.ChatClientFactory;
import es.com.adakadavra.agent.jarvis.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class DirectorAgent {

    private static final Logger logger = LoggerFactory.getLogger(DirectorAgent.class);

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

    private final ChatClientFactory chatClientFactory;
    private final AgentPromptCatalog promptCatalog;
    private final Map<AgentType, Agent> agents;

    public DirectorAgent(
            ChatClientFactory chatClientFactory,
            AgentPromptCatalog promptCatalog,
            List<Agent> agentList) {
        this.chatClientFactory = chatClientFactory;
        this.promptCatalog = promptCatalog;
        this.agents = agentList.stream().collect(Collectors.toMap(Agent::type, Function.identity()));
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
                    AgentType.DIRECTOR,
                    "Handled directly by orchestrator self-intent",
                    orchestratorSelfResponse(resolved),
                    resolved,
                    "director-internal",
                    null));
        }

        ModelProvider resolved = provider != null ? provider : chatClientFactory.defaultProvider();

        return routeReactive(request.message(), provider)
                .flatMap(decision -> {
                    AgentType routedType = resolveRoutedAgentType(decision);
                    String reasoning = decision.reasoning();
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

        return routeReactive(request.message(), provider)
                .flatMapMany(decision -> {
                    AgentType routedType = resolveRoutedAgentType(decision);
                    String reasoning = decision.reasoning() != null
                            ? decision.reasoning().replace("\"", "'") : "";
                    String metaToken = String.format(
                            "[META] {\"routedTo\":\"%s\",\"reasoning\":\"%s\",\"provider\":\"%s\"}",
                            routedType.name(), reasoning, streamResolved.name());
                    return Flux.concat(
                            Flux.just(metaToken),
                            agents.get(routedType).stream(request.message(), cid, provider, request.model()));
                });
    }

    private RoutingDecision route(String message, ModelProvider provider) {
        ModelProvider resolved = provider != null ? provider : chatClientFactory.defaultProvider();
        logger.info("[DIRECTOR] Enrutando petición con proveedor: {}", resolved);

        if (chatClientFactory.usesClaudeCli(resolved)) {
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

    private Mono<RoutingDecision> routeReactive(String message, ModelProvider provider) {
        return Mono.fromCallable(() -> route(message, provider))
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

        return "Soy el DIRECTOR de Jarvis. Coordino y enruto peticiones entre los agentes especializados. "
                + "Actualmente hay " + agents.size() + " agentes disponibles: " + availableAgents + ". "
                + "Proveedor activo: " + provider + ".";
    }

    private AgentType resolveRoutedAgentType(RoutingDecision decision) {
        if (decision != null && decision.agentType() != null && agents.containsKey(decision.agentType())) {
            return decision.agentType();
        }
        return AgentType.DEVELOPER;
    }
}
