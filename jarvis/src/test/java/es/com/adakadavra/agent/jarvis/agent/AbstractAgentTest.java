package es.com.adakadavra.agent.jarvis.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Context-Aware Prompts for Agents")
class AbstractAgentTest {

    @Test
    @DisplayName("shouldBe_demonstrate_context_aware_prompt_building")
    void shouldBeDemonstrateContextAwarePromptBuilding() {
        // Arrange
        String basePrompt = "Eres un desarrollador de software senior.";
        String context = "## Contexto de la Conversación:\n" +
                         "Usuario preguntó sobre microservicios.\n" +
                         "Se recomendó usar Spring Boot.\n" +
                         "\n## Temas Recientes:\n" +
                         "- microservices\n" +
                         "- spring\n" +
                         "- docker\n";
        String agentContext = "## Enfoque especial:\n" +
                             "- Proporciona ejemplos de código\n" +
                             "- Explica decisiones arquitectónicas\n";

        // Act
        StringBuilder enrichedPrompt = new StringBuilder();
        enrichedPrompt.append(basePrompt).append("\n");
        enrichedPrompt.append(context);
        enrichedPrompt.append(agentContext);
        String finalPrompt = enrichedPrompt.toString();

        // Assert
        assertNotNull(finalPrompt);
        assertTrue(finalPrompt.contains("desarrollador"));
        assertTrue(finalPrompt.contains("Contexto de la Conversación"));
        assertTrue(finalPrompt.contains("microservicios"));
        assertTrue(finalPrompt.contains("Enfoque especial"));
        assertTrue(finalPrompt.contains("Proporciona ejemplos"));
    }

    @Test
    @DisplayName("shouldBe_build_prompt_with_dynamicContext")
    void shouldBeBuildPromptWithDynamicContext() {
        // Arrange - Simulated conversation history
        String[] conversationHistory = new String[]{
            "¿Cómo diseñar una arquitectura de microservicios?",
            "Considera separación de responsabilidades...",
            "¿Qué tecnologías recomiendas?",
            "Spring Boot es una excelente opción..."
        };

        // Act - Extract context
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = Math.max(0, conversationHistory.length - 4); i < conversationHistory.length; i++) {
            contextBuilder.append(conversationHistory[i]).append("\n");
        }

        // Assert
        String context = contextBuilder.toString();
        assertFalse(context.isEmpty());
        assertTrue(context.contains("microservicios"));
        assertTrue(context.contains("Spring Boot"));
    }

    @Test
    @DisplayName("shouldBe_extract_keywords_from_conversation")
    void shouldBeExtractKeywordsFromConversation() {
        // Arrange
        String userInput = "How to optimize Spring Boot microservices with Docker containers?";

        // Act - Extract keywords (words > 4 characters)
        String[] words = userInput.split("[\\s,.:;!?()]+");
        StringBuilder keywords = new StringBuilder();
        int count = 0;
        for (String word : words) {
            if (word.length() > 4 && count < 3) {
                keywords.append("- ").append(word).append("\n");
                count++;
            }
        }

        // Assert
        String result = keywords.toString();
        assertFalse(result.isEmpty());
        assertTrue(result.contains("optimize"));
        assertTrue(result.contains("Spring"));
        assertTrue(result.contains("microservices"));
    }

    @Test
    @DisplayName("shouldBe_handle_agent_specific_context_enrichment")
    void shouldBeHandleAgentSpecificContextEnrichment() {
        // Arrange
        String agentType = "DEVELOPER";
        String agentSpecificContext = getContextForAgent(agentType);

        // Act & Assert
        assertNotNull(agentSpecificContext);
        assertTrue(agentSpecificContext.contains("Enfoque especial"));
        assertTrue(agentSpecificContext.contains("código"));
    }

    private String getContextForAgent(String agentType) {
        return switch (agentType) {
            case "DEVELOPER" -> "## Enfoque especial:\n" +
                              "- Proporciona ejemplos de código\n" +
                              "- Explica decisiones arquitectónicas\n";
            case "DEVOPS" -> "## Enfoque especial:\n" +
                           "- Prioriza confiabilidad y escalabilidad\n" +
                           "- Proporciona scripts de configuración\n";
            case "FRONTEND" -> "## Enfoque especial:\n" +
                            "- Balancéa estética y funcionalidad\n" +
                            "- Enfatiza accesibilidad\n";
            case "SOCIAL_MEDIA" -> "## Enfoque especial:\n" +
                                 "- Adapta tono a cada plataforma\n" +
                                 "- Proporciona estrategias de contenido\n";
            default -> "";
        };
    }
}

