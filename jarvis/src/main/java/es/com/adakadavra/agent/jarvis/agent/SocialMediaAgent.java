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
public class SocialMediaAgent extends AbstractAgent {

    public SocialMediaAgent(
            ChatClientFactory chatClientFactory,
            ChatMemory chatMemory,
            @Autowired(required = false) List<ToolCallbackProvider> mcpTools) {
        super(chatClientFactory, chatMemory, mcpTools);
    }

    @Override
    public AgentType type() {
        return AgentType.SOCIAL_MEDIA;
    }

    @Override
    protected String getBaseSystemPrompt() {
        return """
                Eres un experto en redes sociales y mensajería digital.
                Tienes profundo conocimiento en:
                - WhatsApp Business API y automatizaciones
                - Telegram bots y canales
                - Instagram, X (Twitter), LinkedIn, TikTok y Facebook
                - Estrategias de contenido y engagement
                - Gestión de comunidades y moderación
                - Herramientas de analítica de redes sociales
                Responde siempre de forma práctica y accionable.
                """;
    }

    @Override
    protected String getAgentSpecificContext() {
        return "## Enfoque especial:\n" +
                "- Adapta el tono y estilo a cada plataforma\n" +
                "- Proporciona estrategias de contenido comprobadas\n" +
                "- Considera timing y audiencia de cada plataforma\n" +
                "- Enfoca en engagement y métricas de desempeño\n";
    }
}
