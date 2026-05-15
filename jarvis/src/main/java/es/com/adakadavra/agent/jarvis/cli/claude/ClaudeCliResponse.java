package es.com.adakadavra.agent.jarvis.cli.claude;

import es.com.adakadavra.agent.jarvis.model.TokenMetadata;

public record ClaudeCliResponse(String content, String modelUsed, TokenMetadata tokens) {
}


