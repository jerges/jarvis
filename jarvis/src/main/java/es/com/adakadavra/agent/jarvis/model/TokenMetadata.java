package es.com.adakadavra.agent.jarvis.model;

/**
 * Metadata de uso de tokens en una respuesta de IA.
 * Proporciona información detallada sobre el consumo de tokens.
 */
public record TokenMetadata(
        Integer inputTokens,      // Tokens de entrada (prompt)
        Integer outputTokens,     // Tokens de salida (respuesta)
        Integer totalTokens,      // Total de tokens
        Long timestamp            // Timestamp cuando se generó la respuesta (para calcular TPM)
) {
    public TokenMetadata(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        this(inputTokens, outputTokens, totalTokens, System.currentTimeMillis());
    }

    /**
     * Calcula los Tokens Por Minuto (TPM) basado en el tiempo transcurrido desde la generación.
     * @param currentTimeMillis tiempo actual en milisegundos
     * @return TPM estimado (tokens por minuto)
     */
    public Double calculateTPM(long currentTimeMillis) {
        if (totalTokens == null || totalTokens == 0) {
            return 0.0;
        }
        long elapsedSeconds = (currentTimeMillis - timestamp) / 1000;
        if (elapsedSeconds < 1) {
            return (double) totalTokens * 60;  // Si fue muy rápido, extrapolamos
        }
        return (totalTokens * 60.0) / elapsedSeconds;
    }

    /**
     * Retorna una representación legible del uso de tokens.
     */
    public String displayFormat() {
        return String.format("Input: %d | Output: %d | Total: %d",
                inputTokens != null ? inputTokens : 0,
                outputTokens != null ? outputTokens : 0,
                totalTokens != null ? totalTokens : 0);
    }
}

