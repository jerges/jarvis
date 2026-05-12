package es.com.adakadavra.agent.jarvis.api;

import es.com.adakadavra.agent.jarvis.google.GoogleOAuthService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/google")
public class GoogleWorkspaceController {

    private final GoogleOAuthService googleOAuthService;

    public GoogleWorkspaceController(GoogleOAuthService googleOAuthService) {
        this.googleOAuthService = googleOAuthService;
    }

    @GetMapping("/oauth/url")
    public AuthUrlResponse oauthUrl(@RequestParam("conversationId") String conversationId) {
        return new AuthUrlResponse(
                conversationId,
                googleOAuthService.buildAuthorizationUrl(conversationId)
        );
    }

    @GetMapping(value = "/oauth/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> oauthCallback(
            @RequestParam("code") String code,
            @RequestParam("state") String state) {
        String conversationId = googleOAuthService.handleAuthorizationCallback(code, state);
        return ResponseEntity.ok("Google account connected for conversationId=" + conversationId + ". You can close this tab.");
    }

    @GetMapping("/status")
    public GoogleOAuthService.GoogleConnectionStatus status(@RequestParam("conversationId") String conversationId) {
        return googleOAuthService.status(conversationId);
    }

    @GetMapping("/drive/files")
    public List<GoogleOAuthService.GoogleDriveFile> driveFiles(
            @RequestParam("conversationId") String conversationId,
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        return googleOAuthService.listDriveFiles(conversationId, limit);
    }

    @GetMapping("/gmail/messages")
    public List<GoogleOAuthService.GoogleMailMessage> gmailMessages(
            @RequestParam("conversationId") String conversationId,
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        return googleOAuthService.listGmailMessages(conversationId, limit);
    }

    @GetMapping("/calendar/events")
    public List<GoogleOAuthService.GoogleCalendarEvent> calendarEvents(
            @RequestParam("conversationId") String conversationId,
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        return googleOAuthService.listCalendarEvents(conversationId, limit);
    }

    public record AuthUrlResponse(String conversationId, String authorizationUrl) {
    }
}

