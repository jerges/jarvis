package es.com.adakadavra.agent.jarvis.agent;

import es.com.adakadavra.agent.jarvis.config.ChatClientFactory;
import es.com.adakadavra.agent.jarvis.model.AgentRequest;
import es.com.adakadavra.agent.jarvis.model.AgentResponse;
import es.com.adakadavra.agent.jarvis.model.AgentType;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import es.com.adakadavra.agent.jarvis.model.RoutingDecision;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OrchestratorAgent {

    private static final String ROUTING_PROMPT = """
            Eres el orquestador de Jarvis, un sistema multi-agente.
            Tu única tarea es analizar la petición del usuario y decidir qué agente especializado debe manejarla.

            Agentes disponibles:
            - SOCIAL_MEDIA: redes sociales, WhatsApp, Telegram, Instagram, X, LinkedIn, TikTok, mensajería, bots, comunidades, contenido social
            - DEVELOPER: desarrollo de software, código, arquitectura, APIs, bases de datos, debugging, patrones de diseño, backend
            - DEVOPS: infraestructura, Kubernetes, Docker, CI/CD, cloud (AWS/Azure/GCP), Terraform, monitorización, redes, seguridad de infra
            - FRONTEND: frontend web, React, Vue, Angular, HTML, CSS, diseño UI/UX, Figma, accesibilidad, rendimiento web

            Responde únicamente con el JSON que representa tu decisión de enrutamiento.
            """;

    private final ChatClientFactory chatClientFactory;
    private final Map<AgentType, Agent> agents;

    public OrchestratorAgent(ChatClientFactory chatClientFactory, List<Agent> agentList) {
        this.chatClientFactory = chatClientFactory;
        this.agents = agentList.stream().collect(Collectors.toMap(Agent::type, Function.identity()));
    }

    public AgentResponse process(AgentRequest request) {
        String cid = request.conversationId() != null ? request.conversationId() : UUID.randomUUID().toString();
        ModelProvider provider = request.provider();
        RoutingDecision decision = route(request.message(), provider);
        String response = agents.get(decision.agentType()).process(request.message(), cid, provider);
        ModelProvider resolved = provider != null ? provider : chatClientFactory.defaultProvider();
        return new AgentResponse(decision.agentType(), decision.reasoning(), response, resolved);
    }

    public Flux<String> stream(AgentRequest request) {
        String cid = request.conversationId() != null ? request.conversationId() : UUID.randomUUID().toString();
        ModelProvider provider = request.provider();
        RoutingDecision decision = route(request.message(), provider);
        return agents.get(decision.agentType()).stream(request.message(), cid, provider);
    }

    private RoutingDecision route(String message, ModelProvider provider) {
        return chatClientFactory.orchestratorClient(provider)
                .prompt()
                .system(ROUTING_PROMPT)
                .user(message)
                .call()
                .entity(RoutingDecision.class);
    }
}
