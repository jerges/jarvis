package es.com.adakadavra.agent.jarvis.agent;

import es.com.adakadavra.agent.jarvis.model.AgentType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class FrontendAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
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

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final List<ToolCallbackProvider> mcpTools;

    public FrontendAgent(
            @Qualifier("agentClient") ChatClient chatClient,
            ChatMemory chatMemory,
            @Autowired(required = false) List<ToolCallbackProvider> mcpTools) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.mcpTools = mcpTools != null ? mcpTools : List.of();
    }

    @Override
    public AgentType type() {
        return AgentType.FRONTEND;
    }

    @Override
    public String process(String request, String conversationId) {
        return basePrompt(request, conversationId).call().content();
    }

    @Override
    public Flux<String> stream(String request, String conversationId) {
        return basePrompt(request, conversationId).stream().content();
    }

    private ChatClient.ChatClientRequestSpec basePrompt(String request, String conversationId) {
        var prompt = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(request)
                .advisors(new MessageChatMemoryAdvisor(chatMemory, conversationId, 20));
        if (!mcpTools.isEmpty()) {
            prompt = prompt.tools(mcpTools.toArray(new ToolCallbackProvider[0]));
        }
        return prompt;
    }
}
