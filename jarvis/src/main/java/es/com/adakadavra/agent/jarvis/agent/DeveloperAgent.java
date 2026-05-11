package es.com.adakadavra.agent.jarvis.agent;

import es.com.adakadavra.agent.jarvis.config.ChatClientFactory;
import es.com.adakadavra.agent.jarvis.model.AgentType;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeveloperAgent extends AbstractAgent {

    public DeveloperAgent(
            ChatClientFactory chatClientFactory,
            ChatMemory chatMemory,
            @Autowired(required = false) List<ToolCallbackProvider> mcpTools) {
        super(chatClientFactory, chatMemory, mcpTools);
    }

    @Override
    public AgentType type() {
        return AgentType.DEVELOPER;
    }

    @Override
    protected String getBaseSystemPrompt() {
        return """
                Eres un desarrollador de software senior con más de 15 años de experiencia.
                Tienes dominio experto en:
                - Arquitectura de software (microservicios, DDD, CQRS, Event Sourcing)
                - Java, Python, TypeScript, Go y otros lenguajes
                - Spring Boot, FastAPI, Node.js y otros frameworks
                - Patrones de diseño y buenas prácticas (SOLID, Clean Code)
                - Revisión de código, optimización y debugging
                - APIs REST, GraphQL y mensajería (Kafka, RabbitMQ)
                - Bases de datos SQL y NoSQL
                Proporciona soluciones de código limpias, eficientes y bien explicadas.
                """;
    }

    @Override
    protected String getAgentSpecificContext() {
        return "## Enfoque especial:\n" +
                "- Proporciona ejemplos de código cuando sea relevante\n" +
                "- Explica las decisiones arquitectónicas detrás de tus recomendaciones\n" +
                "- Sugiere mejoras en rendimiento y mantenibilidad\n" +
                "- Considera best practices y estándares de la industria\n";
    }
}
