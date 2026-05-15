package es.com.adakadavra.agent.jarvis.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoogleLoginService {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final Map<String, String> pendingStates = new ConcurrentHashMap<>();

    public GoogleLoginService(
            ObjectMapper objectMapper,
            @Value("${jarvis.google.oauth.client-id:}") String clientId,
            @Value("${jarvis.google.oauth.client-secret:}") String clientSecret,
            @Value("${jarvis.auth.google.redirect-uri:http://localhost:8080/api/auth/callback}") String redirectUri) {
        this.restClient = RestClient.builder().build();
        this.objectMapper = objectMapper;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public String buildLoginUrl() {
        validateConfig();
        String state = UUID.randomUUID().toString();
        pendingStates.put(state, state);
        return UriComponentsBuilder.fromUriString(GOOGLE_AUTH_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "select_account")
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    public AuthUser handleCallback(String code, String state) {
        validateConfig();
        if (state == null || pendingStates.remove(state) == null) {
            throw new IllegalArgumentException("Invalid or expired OAuth state");
        }
        String accessToken = exchangeCodeForToken(code);
        return fetchUserInfo(accessToken);
    }

    private String exchangeCodeForToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        String payload = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(payload);
            String token = root.path("access_token").asText();
            if (token.isBlank()) throw new IllegalStateException("No access_token in response");
            return token;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse token response", e);
        }
    }

    private AuthUser fetchUserInfo(String accessToken) {
        String payload = restClient.get()
                .uri(USERINFO_URL)
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(payload);
            return new AuthUser(
                    root.path("sub").asText(),
                    root.path("email").asText(),
                    root.path("name").asText(),
                    root.path("picture").asText(null)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse userinfo response", e);
        }
    }

    private void validateConfig() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Google OAuth not configured. Set jarvis.google.oauth.client-id and client-secret.");
        }
    }
}
