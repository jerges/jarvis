package es.com.adakadavra.agent.jarvis.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {
    private static final Duration TTL = Duration.ofDays(7);
    private final Map<String, UserSession> store = new ConcurrentHashMap<>();

    public UserSession create(AuthUser user) {
        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        UserSession session = new UserSession(sessionId, user, now, now.plus(TTL));
        store.put(sessionId, session);
        return session;
    }

    public Optional<UserSession> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Optional.empty();
        UserSession session = store.get(sessionId);
        if (session == null || session.isExpired()) {
            store.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void delete(String sessionId) {
        store.remove(sessionId);
    }

    public List<UserSession> findAll() {
        store.entrySet().removeIf(e -> e.getValue().isExpired());
        return store.values().stream()
                .sorted(Comparator.comparing(UserSession::createdAt).reversed())
                .toList();
    }
}
