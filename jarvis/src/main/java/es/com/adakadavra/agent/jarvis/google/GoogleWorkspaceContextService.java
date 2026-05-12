package es.com.adakadavra.agent.jarvis.google;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoogleWorkspaceContextService {

    private final GoogleOAuthService googleOAuthService;
    private final boolean contextEnabled;
    private final int maxFiles;
    private final int maxEmails;
    private final int maxEvents;

    public GoogleWorkspaceContextService(
            GoogleOAuthService googleOAuthService,
            @Value("${jarvis.google.context.enabled:true}") boolean contextEnabled,
            @Value("${jarvis.google.context.max-drive-files:3}") int maxFiles,
            @Value("${jarvis.google.context.max-gmail-messages:3}") int maxEmails,
            @Value("${jarvis.google.context.max-calendar-events:5}") int maxEvents) {
        this.googleOAuthService = googleOAuthService;
        this.contextEnabled = contextEnabled;
        this.maxFiles = maxFiles;
        this.maxEmails = maxEmails;
        this.maxEvents = maxEvents;
    }

    public String buildContextSnippet(String conversationId) {
        if (!contextEnabled || !googleOAuthService.isConnected(conversationId)) {
            return "";
        }

        StringBuilder context = new StringBuilder();

        // ⚠️ This header is intentionally directive so the LLM does not ask the user for auth.
        context.append("IMPORTANT: Google Workspace is already authorized and connected.\n")
               .append("You have READ access to the user's Google Drive, Gmail and Calendar.\n")
               .append("Do NOT ask the user to authorize or grant any Google permissions — they are already granted.\n")
               .append("Use the data below to answer directly without prompting for re-authorization.\n\n");

        try {
            List<GoogleOAuthService.GoogleCalendarEvent> events = googleOAuthService.listCalendarEvents(conversationId, maxEvents);
            if (!events.isEmpty()) {
                context.append("Upcoming Google Calendar events:\n");
                for (GoogleOAuthService.GoogleCalendarEvent event : events) {
                    context.append("- ").append(event.summary());
                    if (event.start() != null) {
                        context.append(" at ").append(event.start());
                    }
                    if (event.location() != null && !event.location().isBlank()) {
                        context.append(" @ ").append(event.location());
                    }
                    context.append("\n");
                }
                context.append("\n");
            }
        } catch (Exception ex) {
            context.append("Calendar context unavailable: ").append(ex.getMessage()).append("\n\n");
        }

        try {
            List<GoogleOAuthService.GoogleDriveFile> files = googleOAuthService.listDriveFiles(conversationId, maxFiles);
            if (!files.isEmpty()) {
                context.append("Drive files recently updated:\n");
                for (GoogleOAuthService.GoogleDriveFile file : files) {
                    context.append("- ")
                            .append(file.name() == null || file.name().isBlank() ? "(unnamed)" : file.name())
                            .append(" [").append(file.mimeType() == null ? "unknown" : file.mimeType())
                            .append("]\n");
                }
                context.append("\n");
            }
        } catch (Exception ex) {
            context.append("Drive context unavailable: ").append(ex.getMessage()).append("\n\n");
        }

        try {
            List<GoogleOAuthService.GoogleMailMessage> messages = googleOAuthService.listGmailMessages(conversationId, maxEmails);
            if (!messages.isEmpty()) {
                context.append("Recent Gmail messages:\n");
                for (GoogleOAuthService.GoogleMailMessage message : messages) {
                    context.append("- ")
                            .append(message.subject() == null || message.subject().isBlank() ? "(no subject)" : message.subject())
                            .append(" from ")
                            .append(message.from() == null || message.from().isBlank() ? "unknown sender" : message.from())
                            .append("\n");
                }
            }
        } catch (Exception ex) {
            context.append("Gmail context unavailable: ").append(ex.getMessage()).append("\n");
        }

        return context.toString().trim();
    }
}
