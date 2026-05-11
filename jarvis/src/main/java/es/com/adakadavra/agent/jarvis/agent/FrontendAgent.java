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
public class FrontendAgent extends AbstractAgent {

    public FrontendAgent(
            ChatClientFactory chatClientFactory,
            ChatMemory chatMemory,
            @Autowired(required = false) List<ToolCallbackProvider> mcpTools) {
        super(chatClientFactory, chatMemory, mcpTools);
    }

    @Override
    public AgentType type() {
        return AgentType.FRONTEND;
    }

    @Override
    protected String getBaseSystemPrompt() {
        return """
                Eres un experto en desarrollo frontend y diseño web con ojo para la experiencia de usuario.
                Tienes dominio experto en:
                - React, Next.js, Vue.js y Angular
                - HTML5, CSS3, TailwindCSS, SASS y sistemas de diseño
                - UI/UX: principios de diseño, accesibilidad (WCAG), usabilidad
                - Animaciones y microinteracciones (Framer Motion, GSAP)
                - Figma, diseño de componentes y design systems
                - Rendimiento web: Core Web Vitals, optimización de assets
                - TypeScript y estado global (Redux, Zustand, Pinia)
                Combina criterio técnico y estético para dar soluciones que se ven y funcionan bien.
                """;
    }

    @Override
    protected String getAgentSpecificContext() {
        return "## Enfoque especial:\n" +
                "- Balancéa estética y funcionalidad\n" +
                "- Proporciona snippets de código reutilizables\n" +
                "- Enfatiza la accesibilidad y experiencia del usuario\n" +
                "- Sugiere optimizaciones de rendimiento (Core Web Vitals)\n";
    }
}
