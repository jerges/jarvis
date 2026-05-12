package es.com.adakadavra.agent.jarvis.model;

/**
 * Respuesta de una llamada al Claude CLI.
 *
 * @param response  Texto de respuesta del modelo.
 * @param sessionId ID de sesión — úsalo en la siguiente petición para continuar la conversación.
 * @param costUsd   Coste estimado de la llamada en dólares.
 */
public record CliResponse(String response, String sessionId, double costUsd) {}
