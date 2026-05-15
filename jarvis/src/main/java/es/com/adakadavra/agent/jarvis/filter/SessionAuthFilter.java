package es.com.adakadavra.agent.jarvis.filter;

import es.com.adakadavra.agent.jarvis.auth.AuthController;
import es.com.adakadavra.agent.jarvis.auth.SessionStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class SessionAuthFilter extends OncePerRequestFilter {

    private final SessionStore sessionStore;

    public SessionAuthFilter(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String sessionId = extractCookie(request, AuthController.SESSION_COOKIE);
        if (sessionId != null) {
            sessionStore.find(sessionId).ifPresent(session -> {
                var auth = UsernamePasswordAuthenticationToken.authenticated(
                        session.user(), sessionId, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(request, response);
    }

    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
