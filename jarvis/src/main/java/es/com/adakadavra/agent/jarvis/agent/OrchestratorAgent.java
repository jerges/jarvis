package es.com.adakadavra.agent.jarvis.agent;

import es.com.adakadavra.agent.jarvis.model.AgentResponse;
import es.com.adakadavra.agent.jarvis.model.AgentType;
import es.com.adakadavra.agent.jarvis.model.RoutingDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private final ChatClient orchestratorClient;
    private final Map<AgentType, Agent> agents;

    public OrchestratorAgent(
            @Qualifier("orchestratorClient") ChatClient orchestratorClient,
            List<Agent> agentList) {
        this.orchestratorClient = orchestratorClient;
        this.agents = agentList.stream().collect(Collectors.toMap(Agent::type, Function.identity()));
    }

    public AgentResponse process(String request, String conversationId) {
        String cid = conversationId != null ? conversationId : UUID.randomUUID().toString();
        RoutingDecision decision = route(request);
        String response = agents.get(decision.agentType()).process(request, cid);
        return new AgentResponse(decision.agentType(), decision.reasoning(), response);
    }

    public Flux<String> stream(String request, String conversationId) {
        String cid = conversationId != null ? conversationId : UUID.randomUUID().toString();
        RoutingDecision decision = route(request);
        return agents.get(decision.agentType()).stream(request, cid);
    }

    private RoutingDecision route(String request) {
        return orchestratorClient.prompt()
                .system(ROUTING_PROMPT)
                .user(request)
                .call()
                .entity(RoutingDecision.class);
    }
}
