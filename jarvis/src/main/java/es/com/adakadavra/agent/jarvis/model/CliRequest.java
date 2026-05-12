package es.com.adakadavra.agent.jarvis.model;

/**
 * Request para invocar el Claude CLI directamente.
 *
 * @param message    Mensaje / prompt del usuario.
 * @param sessionId  ID de sesión previa para continuar la conversación (opcional).
 * @param model      Alias o nombre completo del modelo: "sonnet", "opus", "haiku",
 *                   "claude-sonnet-4-6", etc. (opcional — por defecto "sonnet").
 */
public record CliRequest(String message, String sessionId, String model) {}
