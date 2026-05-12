package es.com.adakadavra.agent.jarvis.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoogleOAuthService {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files";
    private static final String GMAIL_MESSAGES_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
    private static final String CALENDAR_EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events";
    private static final String GLOBAL_CONNECTION_KEY = "__global__";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String driveScope;
    private final String gmailScope;
    private final String calendarScope;
    private final boolean sharedConnectionEnabled;

    private final Map<String, OAuthToken> tokensByConversation = new ConcurrentHashMap<>();
    private final Map<String, String> pendingStates = new ConcurrentHashMap<>();

    public GoogleOAuthService(
            ObjectMapper objectMapper,
            @Value("${jarvis.google.oauth.client-id:}") String clientId,
            @Value("${jarvis.google.oauth.client-secret:}") String clientSecret,
            @Value("${jarvis.google.oauth.redirect-uri:http://localhost:8080/api/google/oauth/callback}") String redirectUri,
            @Value("${jarvis.google.oauth.scope-drive:https://www.googleapis.com/auth/drive.metadata.readonly}") String driveScope,
            @Value("${jarvis.google.oauth.scope-gmail:https://www.googleapis.com/auth/gmail.readonly}") String gmailScope,
            @Value("${jarvis.google.oauth.scope-calendar:https://www.googleapis.com/auth/calendar.readonly}") String calendarScope,
            @Value("${jarvis.google.oauth.shared-connection:true}") boolean sharedConnectionEnabled) {
        this.restClient = RestClient.builder().build();
        this.objectMapper = objectMapper;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.driveScope = driveScope;
        this.gmailScope = gmailScope;
        this.calendarScope = calendarScope;
        this.sharedConnectionEnabled = sharedConnectionEnabled;
    }

    public String buildAuthorizationUrl(String conversationId) {
        validateOAuthConfiguration();
        if (!sharedConnectionEnabled && (conversationId == null || conversationId.isBlank())) {
            throw new IllegalArgumentException("conversationId is required");
        }

        String storageKey = resolveStorageKey(conversationId);
        String state = UUID.randomUUID().toString();
        pendingStates.put(state, storageKey);

        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", driveScope + " " + gmailScope + " " + calendarScope)
                .queryParam("access_type", "offline")
                .queryParam("include_granted_scopes", "true")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    public String handleAuthorizationCallback(String code, String state) {
        validateOAuthConfiguration();
        String storageKey = pendingStates.remove(state);
        if (storageKey == null) {
            throw new IllegalArgumentException("Invalid or expired OAuth state");
        }

        OAuthToken token = exchangeAuthorizationCode(code);
        tokensByConversation.put(storageKey, token);
        return storageKey;
    }

    public GoogleConnectionStatus status(String conversationId) {
        String lookupKey = resolveLookupKey(conversationId);
        OAuthToken token = tokensByConversation.get(lookupKey);
        if (token == null) {
            return new GoogleConnectionStatus(false, conversationId, null);
        }
        return new GoogleConnectionStatus(true, conversationId, token.expiresAt());
    }

    public boolean isConnected(String conversationId) {
        return tokensByConversation.containsKey(resolveLookupKey(conversationId));
    }

    public List<GoogleDriveFile> listDriveFiles(String conversationId, int limit) {
        String accessToken = ensureValidAccessToken(conversationId);
        URI uri = UriComponentsBuilder.fromUriString(DRIVE_FILES_URL)
                .queryParam("pageSize", Math.max(1, Math.min(limit, 20)))
                .queryParam("fields", "files(id,name,mimeType,webViewLink)")
                .queryParam("orderBy", "modifiedTime desc")
                .build()
                .encode()
                .toUri();

        String payload = restClient.get()
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(payload);
            List<GoogleDriveFile> files = new ArrayList<>();
            for (JsonNode fileNode : root.path("files")) {
                files.add(new GoogleDriveFile(
                        fileNode.path("id").asText(),
                        fileNode.path("name").asText(),
                        fileNode.path("mimeType").asText(),
                        fileNode.path("webViewLink").asText(null)
                ));
            }
            return files;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse Google Drive response", ex);
        }
    }

    public List<GoogleCalendarEvent> listCalendarEvents(String conversationId, int limit) {
        String accessToken = ensureValidAccessToken(conversationId);

        String timeMin = java.time.Instant.now().toString();
        URI uri = UriComponentsBuilder.fromUriString(CALENDAR_EVENTS_URL)
                .queryParam("maxResults", Math.max(1, Math.min(limit, 20)))
                .queryParam("orderBy", "startTime")
                .queryParam("singleEvents", "true")
                .queryParam("timeMin", timeMin)
                .build()
                .encode()
                .toUri();

        String payload = restClient.get()
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(payload);
            List<GoogleCalendarEvent> events = new ArrayList<>();
            for (JsonNode item : root.path("items")) {
                String start = item.path("start").path("dateTime").asText(
                        item.path("start").path("date").asText(null));
                events.add(new GoogleCalendarEvent(
                        item.path("id").asText(),
                        item.path("summary").asText("(no title)"),
                        start,
                        item.path("end").path("dateTime").asText(
                                item.path("end").path("date").asText(null)),
                        item.path("location").asText(null)
                ));
            }
            return events;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse Google Calendar response", ex);
        }
    }

    public List<GoogleMailMessage> listGmailMessages(String conversationId, int limit) {
        String accessToken = ensureValidAccessToken(conversationId);
        URI uri = UriComponentsBuilder.fromUriString(GMAIL_MESSAGES_URL)
                .queryParam("maxResults", Math.max(1, Math.min(limit, 20)))
                .build(true)
                .toUri();

        String payload = restClient.get()
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(payload);
            List<GoogleMailMessage> messages = new ArrayList<>();
            for (JsonNode msgRef : root.path("messages")) {
                String messageId = msgRef.path("id").asText();
                if (messageId == null || messageId.isBlank()) {
                    continue;
                }
                messages.add(fetchGmailMessageDetails(accessToken, messageId));
            }
            return messages;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse Gmail response", ex);
        }
    }

    private GoogleMailMessage fetchGmailMessageDetails(String accessToken, String messageId) {
        URI uri = UriComponentsBuilder.fromUriString(GMAIL_MESSAGES_URL + "/" + messageId)
                .queryParam("format", "metadata")
                .queryParam("metadataHeaders", "From")
                .queryParam("metadataHeaders", "Subject")
                .build(true)
                .toUri();

        String payload = restClient.get()
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(payload);
            String from = null;
            String subject = null;
            for (JsonNode header : root.path("payload").path("headers")) {
                String name = header.path("name").asText("");
                if ("From".equalsIgnoreCase(name)) {
                    from = header.path("value").asText();
                } else if ("Subject".equalsIgnoreCase(name)) {
                    subject = header.path("value").asText();
                }
            }
            return new GoogleMailMessage(
                    root.path("id").asText(),
                    root.path("threadId").asText(),
                    from,
                    subject,
                    root.path("snippet").asText("")
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse Gmail message details", ex);
        }
    }

    private String ensureValidAccessToken(String conversationId) {
        String lookupKey = resolveLookupKey(conversationId);
        OAuthToken token = tokensByConversation.get(lookupKey);
        if (token == null) {
            throw new IllegalStateException("Google account is not connected for key=" + lookupKey);
        }

        Instant now = Instant.now();
        if (token.expiresAt().isAfter(now.plusSeconds(30))) {
            return token.accessToken();
        }

        if (token.refreshToken() == null || token.refreshToken().isBlank()) {
            throw new IllegalStateException("Google access token expired and refresh token is unavailable");
        }

        OAuthToken refreshed = refreshAccessToken(token.refreshToken());
        tokensByConversation.put(lookupKey, refreshed);
        return refreshed.accessToken();
    }

    private String resolveStorageKey(String conversationId) {
        if (sharedConnectionEnabled) {
            return GLOBAL_CONNECTION_KEY;
        }
        return conversationId;
    }

    private String resolveLookupKey(String conversationId) {
        if (sharedConnectionEnabled) {
            return GLOBAL_CONNECTION_KEY;
        }
        return conversationId;
    }

    private OAuthToken exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        return requestToken(form, null);
    }

    private OAuthToken refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");

        return requestToken(form, refreshToken);
    }

    private OAuthToken requestToken(MultiValueMap<String, String> form, String fallbackRefreshToken) {
        String payload = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(payload);
            String accessToken = root.path("access_token").asText();
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("Google token response does not contain access_token");
            }
            String refreshToken = root.path("refresh_token").asText(null);
            if (refreshToken == null || refreshToken.isBlank()) {
                refreshToken = fallbackRefreshToken;
            }
            long expiresIn = root.path("expires_in").asLong(3600);
            return new OAuthToken(accessToken, refreshToken, Instant.now().plusSeconds(expiresIn));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse Google OAuth token response", ex);
        }
    }

    private void validateOAuthConfiguration() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Google OAuth is not configured. Set jarvis.google.oauth.client-id and jarvis.google.oauth.client-secret");
        }
    }

    private record OAuthToken(String accessToken, String refreshToken, Instant expiresAt) {
    }

    public record GoogleConnectionStatus(boolean connected, String conversationId, Instant expiresAt) {
    }

    public record GoogleDriveFile(String id, String name, String mimeType, String webViewLink) {
    }

    public record GoogleMailMessage(String id, String threadId, String from, String subject, String snippet) {
    }

    public record GoogleCalendarEvent(String id, String summary, String start, String end, String location) {
    }
}
