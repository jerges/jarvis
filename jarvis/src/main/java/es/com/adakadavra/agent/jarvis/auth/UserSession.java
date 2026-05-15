package es.com.adakadavra.agent.jarvis.auth;

import java.time.Instant;

public record UserSession(String sessionId, AuthUser user, Instant createdAt, Instant expiresAt) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
