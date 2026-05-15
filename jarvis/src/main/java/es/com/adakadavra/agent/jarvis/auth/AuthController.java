package es.com.adakadavra.agent.jarvis.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String SESSION_COOKIE = "jarvis_session";

    private final GoogleLoginService loginService;
    private final SessionStore sessionStore;
    private final String frontendUrl;

    public AuthController(
            GoogleLoginService loginService,
            SessionStore sessionStore,
            @Value("${jarvis.auth.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.loginService = loginService;
        this.sessionStore = sessionStore;
        this.frontendUrl = frontendUrl;
    }

    @GetMapping("/login-url")
    public ResponseEntity<Map<String, String>> loginUrl() {
        return ResponseEntity.ok(Map.of("loginUrl", loginService.buildLoginUrl()));
    }

    @GetMapping("/callback")
    public void callback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response) throws IOException {
        AuthUser user = loginService.handleCallback(code, state);
        UserSession session = sessionStore.create(user);
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, session.sessionId())
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.sendRedirect(frontendUrl);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUser> me(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication auth, HttpServletResponse response) {
        if (auth != null && auth.getCredentials() instanceof String sessionId) {
            sessionStore.delete(sessionId);
        }
        ResponseCookie clear = ResponseCookie.from(SESSION_COOKIE, "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clear.toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<UserSession>> sessions(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(sessionStore.findAll());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable String sessionId,
            Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser)) {
            return ResponseEntity.status(401).build();
        }
        sessionStore.delete(sessionId);
        return ResponseEntity.noContent().build();
    }
}
