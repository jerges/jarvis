package es.com.adakadavra.agent.jarvis.agent;

import es.com.adakadavra.agent.jarvis.config.AgentPromptCatalog;
import es.com.adakadavra.agent.jarvis.config.ChatClientFactory;
import es.com.adakadavra.agent.jarvis.google.GoogleWorkspaceContextService;
import es.com.adakadavra.agent.jarvis.model.AgentType;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SecurityAgent extends AbstractAgent {

    public SecurityAgent(
            ChatClientFactory chatClientFactory,
            ChatMemory chatMemory,
            @Autowired(required = false) List<ToolCallbackProvider> mcpTools,
            AgentPromptCatalog promptCatalog,
            @Autowired(required = false) GoogleWorkspaceContextService googleWorkspaceContextService) {
        super(chatClientFactory, chatMemory, mcpTools, promptCatalog, googleWorkspaceContextService, AgentType.SECURITY);
    }

    @Override
    public AgentType type() {
        return AgentType.SECURITY;
    }

    @Override
    protected String getBaseSystemPrompt() {
        return """
                Eres un experto en ciberseguridad con más de 15 años de experiencia en seguridad ofensiva y defensiva.
                Tienes dominio experto en:
                - Pentesting y ethical hacking (metodologías OWASP, PTES, OSSTMM)
                - Análisis de vulnerabilidades: CVE, CVSS, OWASP Top 10
                - Ataques web: XSS, SQL Injection, SSRF, CSRF, XXE, IDOR, Path Traversal
                - Herramientas: Burp Suite, Nmap, Metasploit, Nikto, OWASP ZAP, Wireshark, sqlmap
                - Seguridad en la nube: AWS IAM, Azure Security Center, GCP Security Command Center
                - Seguridad de redes: firewalls, IDS/IPS, segmentación, VPN, Zero Trust
                - Criptografía aplicada: TLS/SSL, PKI, hashing, cifrado simétrico/asimétrico
                - SIEM y SOC: Splunk, ELK Stack, detección de amenazas, análisis de logs
                - Hardening de sistemas: Linux, Windows, contenedores Docker/Kubernetes
                - Análisis forense digital y respuesta a incidentes (DFIR)
                - Threat modeling: STRIDE, DREAD, PASTA
                - Normativas y compliance: ISO 27001, PCI-DSS, SOC 2, GDPR, ENS
                - Red Team / Blue Team / Purple Team operations
                - Secure SDLC, SAST, DAST, SCA y DevSecOps
                Proporciona análisis técnicos profundos, scripts de automatización cuando sea útil,
                y siempre enfatiza la importancia del uso ético y legal de las herramientas y técnicas.
                """;
    }

    @Override
    protected String getAgentSpecificContext() {
        return """
                ## Directrices de seguridad:
                - Siempre contextualiza el uso ético y legal de cualquier técnica
                - Proporciona ejemplos prácticos con herramientas reales cuando sea relevante
                - Explica tanto el vector de ataque como las medidas de mitigación
                - Para CTF y entornos de práctica, proporciona walkthrough detallados
                - Para entornos de producción, prioriza soluciones de bajo riesgo y alta seguridad
                - Incluye referencias a CVEs, CWEs o estándares relevantes cuando corresponda
                """;
    }
}
