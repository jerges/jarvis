package es.com.adakadavra.agent.jarvis.agent;

import es.com.adakadavra.agent.jarvis.config.ChatClientFactory;
import es.com.adakadavra.agent.jarvis.model.AgentType;
import es.com.adakadavra.agent.jarvis.model.ModelProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class DevOpsAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            Eres un experto en infraestructura y DevOps con amplia experiencia en entornos cloud.
            Tienes dominio experto en:
            - Kubernetes, Docker y orquestación de contenedores
            - CI/CD: GitHub Actions, GitLab CI, Jenkins, ArgoCD
            - Cloud providers: AWS, Azure, GCP
            - Infrastructure as Code: Terraform, Pulumi, Ansible
            - Observabilidad: Prometheus, Grafana, ELK Stack, OpenTelemetry
            - Seguridad en infraestructura y DevSecOps
            - Redes, load balancing y alta disponibilidad
            Ofrece soluciones robustas, escalables y seguras.
            """;

    private final ChatClientFactory chatClientFactory;
    private final ChatMemory chatMemory;
    private final List<ToolCallbackProvider> mcpTools;

    public DevOpsAgent(
            ChatClientFactory chatClientFactory,
            ChatMemory chatMemory,
            @Autowired(required = false) List<ToolCallbackProvider> mcpTools) {
        this.chatClientFactory = chatClientFactory;
        this.chatMemory = chatMemory;
        this.mcpTools = mcpTools != null ? mcpTools : List.of();
    }

    @Override
    public AgentType type() {
        return AgentType.DEVOPS;
    }

    @Override
    public String process(String request, String conversationId, ModelProvider provider) {
        return basePrompt(request, conversationId, provider).call().content();
    }

    @Override
    public Flux<String> stream(String request, String conversationId, ModelProvider provider) {
        return basePrompt(request, conversationId, provider).stream().content();
    }

    private ChatClient.ChatClientRequestSpec basePrompt(String request, String conversationId, ModelProvider provider) {
        var prompt = chatClientFactory.agentClient(provider)
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(request)
                .advisors(new MessageChatMemoryAdvisor(chatMemory, conversationId, 20));
        if (!mcpTools.isEmpty()) {
            prompt = prompt.tools(mcpTools.toArray(new ToolCallbackProvider[0]));
        }
        return prompt;
    }
}
