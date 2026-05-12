import { Component, ElementRef, ViewChild, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { JarvisService } from './services/jarvis.service';
import { AgentType, ChatMessage, AGENT_META } from './models/agent.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  @ViewChild('messagesEnd') messagesEnd!: ElementRef;

  message = '';
  conversationId = this.newConversationId();
  streamMode = false;
  loading = false;
  availableModels = ['gemma4:latest', 'qwen3-coder:30b'];
  selectedModel = 'gemma4:latest';
  backendModeLabel = 'Ollama';
  googleConnected = false;
  googleStatusLabel = 'Not connected';

  messages = signal<ChatMessage[]>([]);
  agentMeta = AGENT_META;
  directorAgent: AgentType = 'DIRECTOR';
  subAgents: AgentType[] = ['SECRETARY', 'DEVELOPER', 'DEVOPS', 'SOCIAL_MEDIA', 'FRONTEND'];

  agentColor = computed(() => {
    const last = this.messages().findLast((m: ChatMessage) => m.routedTo);
    return last?.routedTo ? AGENT_META[last.routedTo].color : '#6366f1';
  });

  lastRoutedAgent = computed<AgentType | null>(() => {
    const last = this.messages().findLast((m: ChatMessage) => m.routedTo);
    return last?.routedTo ?? null;
  });

  constructor(private jarvis: JarvisService) {}

  ngOnInit(): void {
    this.refreshGoogleStatus();

    this.jarvis.capabilities().subscribe({
      next: capabilities => {
        if (capabilities.allowedModels.length > 0) {
          this.availableModels = capabilities.allowedModels;
        }

        this.backendModeLabel = capabilities.cliMode ? 'Claude CLI' : 'Ollama';

        if (!this.availableModels.includes(this.selectedModel)) {
          this.selectedModel = this.availableModels[0] ?? '';
        }
      },
      error: () => {
        // Keep UI defaults when capabilities endpoint is not available.
      }
    });
  }

  connectGoogleWorkspace(): void {
    // Open popup during click event to prevent popup blockers in async callbacks.
    const authWindow = window.open('', '_blank', 'noopener,noreferrer');

    this.jarvis.googleAuthUrl(this.conversationId).subscribe({
      next: response => {
        if (authWindow) {
          authWindow.location.href = response.authorizationUrl;
          return;
        }

        // Popup blocked: continue auth in current tab.
        window.location.href = response.authorizationUrl;
      },
      error: () => {
        if (authWindow && !authWindow.closed) {
          authWindow.close();
        }
        this.googleConnected = false;
        this.googleStatusLabel = 'OAuth unavailable';
      }
    });
  }

  send(): void {
    const text = this.message.trim();
    if (!text || this.loading) return;

    this.messages.update(msgs => [...msgs, { role: 'user', content: text }]);
    this.message = '';
    this.loading = true;
    this.scrollToBottom();

    const request = { message: text, conversationId: this.conversationId, model: this.selectedModel };

    if (this.streamMode) {
      const assistantMsg: ChatMessage = {
        role: 'assistant',
        content: '',
        streaming: true,
        modelUsed: this.selectedModel,
      };
      this.messages.update(msgs => [...msgs, assistantMsg]);

      this.jarvis.stream(request).subscribe({
        next: token => {
          this.messages.update(msgs => {
            const updated = [...msgs];
            const last = { ...updated[updated.length - 1] };
            last.content += token;
            updated[updated.length - 1] = last;
            return updated;
          });
          this.scrollToBottom();
        },
        complete: () => {
          this.messages.update(msgs => {
            const updated = [...msgs];
            updated[updated.length - 1] = { ...updated[updated.length - 1], streaming: false };
            return updated;
          });
          this.loading = false;
        },
        error: () => this.loading = false,
      });
    } else {
       this.jarvis.chat(request).subscribe({
         next: res => {
           this.messages.update(msgs => [...msgs, {
             role: 'assistant',
             content: res.response,
             routedTo: res.routedTo,
             reasoning: res.reasoning,
             modelUsed: res.modelUsed,
             tokens: res.tokens,
           }]);
           this.loading = false;
           this.scrollToBottom();
         },
         error: () => this.loading = false,
       });
    }
  }

  newChat(): void {
    this.messages.set([]);
    this.conversationId = this.newConversationId();
    this.googleConnected = false;
    this.googleStatusLabel = 'Not connected';
    this.refreshGoogleStatus();
  }

  refreshGoogleStatus(): void {
    this.jarvis.googleConnectionStatus(this.conversationId).subscribe({
      next: status => {
        this.googleConnected = status.connected;
        this.googleStatusLabel = status.connected ? 'Connected' : 'Not connected';
      },
      error: () => {
        this.googleConnected = false;
        this.googleStatusLabel = 'OAuth unavailable';
      }
    });
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  agentBadge(type: AgentType) {
    return AGENT_META[type];
  }

  private newConversationId(): string {
    return crypto.randomUUID();
  }

  private scrollToBottom(): void {
    setTimeout(() => this.messagesEnd?.nativeElement.scrollIntoView({ behavior: 'smooth' }), 50);
  }
}
