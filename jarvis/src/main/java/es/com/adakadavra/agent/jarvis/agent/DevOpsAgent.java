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
public class DevOpsAgent extends AbstractAgent {

    public DevOpsAgent(
            ChatClientFactory chatClientFactory,
            ChatMemory chatMemory,
            @Autowired(required = false) List<ToolCallbackProvider> mcpTools) {
        super(chatClientFactory, chatMemory, mcpTools);
    }

    @Override
    public AgentType type() {
        return AgentType.DEVOPS;
    }

    @Override
    protected String getBaseSystemPrompt() {
        return """
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
    }

    @Override
    protected String getAgentSpecificContext() {
        return "## Enfoque especial:\n" +
                "- Prioriza la confiabilidad, escalabilidad y observabilidad\n" +
                "- Proporciona scripts de configuración cuando sea aplicable\n" +
                "- Sugiere mejoras en seguridad y disaster recovery\n" +
                "- Considera cost-optimization sin comprometer rendimiento\n";
    }
}
