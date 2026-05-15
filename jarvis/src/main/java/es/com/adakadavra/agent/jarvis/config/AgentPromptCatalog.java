package es.com.adakadavra.agent.jarvis.config;

import es.com.adakadavra.agent.jarvis.model.AgentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component
public class AgentPromptCatalog {

    private static final Logger logger = LoggerFactory.getLogger(AgentPromptCatalog.class);

    private final String promptsRoot;

    public AgentPromptCatalog(@Value("${jarvis.agents.prompts-root:classpath:agents}") String promptsRoot) {
        this.promptsRoot = promptsRoot;
    }

    public String basePrompt(AgentType agentType, String fallback) {
        return readPrompt(agentType, "base.md", fallback);
    }

    public String routingPrompt(String fallback) {
        return readPrompt(AgentType.JARVIS, "routing.md", fallback);
    }

    public String enrichedAgentContext(AgentType agentType, String fallbackContext) {
        List<String> sections = new ArrayList<>();

        appendSection(sections, "Operating Instructions", readPrompt(agentType, "instructions.md", ""));
        appendSection(sections, "Skills", readPrompt(agentType, "skills.md", ""));
        appendSection(sections, "MCP Tools", readPrompt(agentType, "mcp.md", ""));

        if (fallbackContext != null && !fallbackContext.isBlank()) {
            sections.add(fallbackContext);
        }

        return String.join("\n\n", sections);
    }

    public String guardPrompt(AgentType agentType, String fallback) {
        return readPrompt(agentType, "guard.md", fallback);
    }

    private void appendSection(List<String> sections, String title, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        sections.add("## " + title + "\n" + content.strip());
    }

    private String readPrompt(AgentType agentType, String fileName, String fallback) {
        String relativePath = "%s/%s".formatted(agentFolder(agentType), fileName);
        try {
            String content = readText(relativePath);
            if (content == null || content.isBlank()) {
                return fallback;
            }
            return content.strip();
        } catch (IOException ex) {
            logger.debug("Prompt file not found for {} at {}. Using fallback.", agentType, relativePath);
            return fallback;
        }
    }

    private String readText(String relativePath) throws IOException {
        if (promptsRoot.startsWith("classpath:")) {
            String basePath = promptsRoot.substring("classpath:".length());
            String normalizedBase = trimSlashes(basePath);
            String fullPath = normalizedBase.isEmpty() ? relativePath : normalizedBase + "/" + relativePath;
            ClassPathResource resource = new ClassPathResource(fullPath);
            if (!resource.exists()) {
                throw new IOException("Resource not found: " + fullPath);
            }
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }

        Path fullPath = Paths.get(promptsRoot).resolve(relativePath).normalize();
        if (!Files.exists(fullPath)) {
            throw new IOException("Path not found: " + fullPath);
        }
        return Files.readString(fullPath, StandardCharsets.UTF_8);
    }

    private String trimSlashes(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String value = input;
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String agentFolder(AgentType agentType) {
        return switch (agentType) {
            case JARVIS -> "orchestrator";
            case DEVELOPER -> "developer";
            case DEVOPS -> "devops";
            case FRONTEND -> "frontend";
            case SECRETARY -> "secretary";
            case SOCIAL_MEDIA -> "social-media";
            case SECURITY -> "security";
        };
    }
}

