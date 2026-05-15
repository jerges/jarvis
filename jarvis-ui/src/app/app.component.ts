import { Component, ElementRef, ViewChild, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { JarvisService } from './services/jarvis.service';
import {
  AgentType, AppMode, ChatMessage, CliProvider, CliProviderStatus,
  ConnectionCapabilities, ConnectionType, Plan, AGENT_META
} from './models/agent.model';

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
  googleConnected = false;
  googleStatusLabel = 'Not connected';

  messages       = signal<ChatMessage[]>([]);
  plans          = signal<Plan[]>([]);
  selectedPlanId = signal<string | null>(null);
  appMode        = signal<AppMode>('agent');

  connectionType: ConnectionType = 'api';
  heuristicRoutingEnabled = false;
  heuristicMinKeywordHits = 2;
  connectionCapabilities: Record<ConnectionType, ConnectionCapabilities> = {
    'api': { label: 'API (Anthropic)', models: ['claude-sonnet-4-6'], provider: 'ANTHROPIC', heuristicSupported: false, heuristicRequired: false },
    'ollama': { label: 'Ollama local', models: ['gemma4:latest', 'qwen3-coder:30b'], provider: 'OLLAMA', heuristicSupported: false, heuristicRequired: false },
    'claude-cli': { label: 'Claude CLI', models: ['haiku', 'sonnet', 'opus'], provider: 'CLAUDE_CLI', heuristicSupported: true, heuristicRequired: false },
    'copilot-cli': { label: 'Copilot CLI', models: ['default'], provider: 'COPILOT_CLI', heuristicSupported: true, heuristicRequired: true },
  };
  selectedModels: Record<ConnectionType, string> = {
    'api': 'claude-sonnet-4-6',
    'ollama': 'gemma4:latest',
    'claude-cli': 'haiku',
    'copilot-cli': 'default',
  };
  cliProviderStatus = signal<Record<CliProvider, CliProviderStatus | null>>({
    copilot: null,
    claude: null,
  });
  uiError = '';

  agentMeta = AGENT_META;
  coordinatorAgent: AgentType = 'JARVIS';
  subAgents: AgentType[] = ['SECRETARY', 'SECURITY', 'DEVELOPER', 'DEVOPS', 'SOCIAL_MEDIA', 'FRONTEND'];

  agentColor = computed(() => {
    const last = this.messages().findLast((m: ChatMessage) => m.routedTo);
    return last?.routedTo ? AGENT_META[last.routedTo].color : '#6366f1';
  });

  lastRoutedAgent = computed<AgentType | null>(() => {
    const last = this.messages().findLast((m: ChatMessage) => m.routedTo);
    return last?.routedTo ?? null;
  });

  selectedPlan = computed(() => {
    const id = this.selectedPlanId();
    return id ? this.plans().find(p => p.id === id) ?? null : null;
  });

  planCount = computed(() => this.plans().length);

  constructor(private readonly jarvis: JarvisService) {}

  ngOnInit(): void {
    this.refreshGoogleStatus();
    this.refreshCliProviderStatus();

    this.jarvis.capabilities().subscribe({
      next: capabilities => {
        this.connectionCapabilities = capabilities.connections;
        this.syncSelectedModelsWithCapabilities();
        this.connectionType = this.resolveDefaultConnectionType(capabilities.defaultProvider);
        this.syncHeuristicModeWithConnection(this.connectionType);
      },
      error: () => {}
    });
  }

  // ── Mode switching ────────────────────────────────────────────────────────

  setMode(mode: AppMode): void {
    this.appMode.set(mode);
    this.uiError = '';
    if (mode !== 'plan') this.selectedPlanId.set(null);
  }

  setConnectionType(connectionType: ConnectionType): void {
    this.connectionType = connectionType;
    this.syncHeuristicModeWithConnection(connectionType);
    this.uiError = '';
    if (connectionType === 'claude-cli' || connectionType === 'copilot-cli') {
      const provider = this.currentCliProvider();
      if (provider) {
        this.refreshCliProviderStatus(provider);
      }
    }
  }

  // ── Plan management ───────────────────────────────────────────────────────

  selectPlan(plan: Plan): void {
    this.selectedPlanId.set(this.selectedPlanId() === plan.id ? null : plan.id);
  }

  selectPlanById(id: string): void {
    const plan = this.plans().find(p => p.id === id);
    if (plan) this.selectPlan(plan);
  }

  deletePlan(plan: Plan, event: Event): void {
    event.stopPropagation();
    this.plans.update(ps => ps.filter(p => p.id !== plan.id));
    if (this.selectedPlanId() === plan.id) this.selectedPlanId.set(null);
  }

  loadPlanIntoChat(plan: Plan): void {
    this.message = plan.userMessage;
  }

  clearPlans(): void {
    this.plans.set([]);
    this.selectedPlanId.set(null);
  }

  // ── Google Workspace ──────────────────────────────────────────────────────

  connectGoogleWorkspace(): void {
    const authWindow = window.open('', '_blank', 'noopener,noreferrer');

    this.jarvis.googleAuthUrl(this.conversationId).subscribe({
      next: response => {
        if (authWindow) {
          authWindow.location.href = response.authorizationUrl;
          return;
        }
        window.location.href = response.authorizationUrl;
      },
      error: () => {
        if (authWindow && !authWindow.closed) authWindow.close();
        this.googleConnected = false;
        this.googleStatusLabel = 'OAuth unavailable';
      }
    });
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

  // ── Sending messages ──────────────────────────────────────────────────────

  send(): void {
    const text = this.message.trim();
    if (!text || this.loading) return;

    this.uiError = '';

    this.messages.update(msgs => [...msgs, { role: 'user', content: text }]);
    this.message = '';
    this.loading = true;
    this.scrollToBottom();

    if (!this.ensureConnectionReady()) {
      this.loading = false;
      return;
    }

    const request = {
      message: text,
      conversationId: this.conversationId,
      model: this.selectedModelForCurrentConnection(),
      provider: this.resolveModelProvider(),
      heuristicRoutingEnabled: this.supportsHeuristicRouting() ? this.heuristicRoutingEnabled : undefined,
      heuristicMinKeywordHits: this.supportsHeuristicRouting() ? this.heuristicMinKeywordHits : undefined,
    };

    if (this.streamMode) {
      const assistantMsg: ChatMessage = {
        role: 'assistant',
        content: '',
        streaming: true,
        modelUsed: this.selectedModelForCurrentConnection(),
      };
      this.messages.update(msgs => [...msgs, assistantMsg]);

      this.jarvis.stream(request).subscribe({
        next: token => {
          if (token.startsWith('[META] ')) {
            try {
              const meta = JSON.parse(token.slice(7));
              this.messages.update(msgs => {
                const updated = [...msgs];
                updated[updated.length - 1] = {
                  ...updated[updated.length - 1],
                  routedTo: meta.routedTo,
                  reasoning: meta.reasoning,
                };
                return updated;
              });
            } catch { /* ignore parse errors */ }
          } else {
            this.messages.update(msgs => {
              const updated = [...msgs];
              const last = { ...updated[updated.length - 1] };
              last.content += token;
              updated[updated.length - 1] = last;
              return updated;
            });
            this.scrollToBottom();
          }
        },
        complete: () => {
          this.messages.update(msgs => {
            const updated = [...msgs];
            const finished = { ...updated[updated.length - 1], streaming: false };
            if (this.appMode() === 'plan') {
              const plan = this.buildPlan(text, finished.content, finished.routedTo);
              finished.planId = plan.id;
              this.plans.update(ps => [plan, ...ps]);
              this.selectedPlanId.set(plan.id);
            }
            updated[updated.length - 1] = finished;
            return updated;
          });
          this.loading = false;
        },
        error: error => {
          this.handleChatError(error);
          this.loading = false;
        },
      });
    } else {
      this.jarvis.chat(request).subscribe({
        next: res => {
          const planId = this.appMode() === 'plan'
            ? (() => {
                const plan = this.buildPlan(text, res.response, res.routedTo);
                this.plans.update(ps => [plan, ...ps]);
                this.selectedPlanId.set(plan.id);
                return plan.id;
              })()
            : undefined;

          this.messages.update(msgs => [...msgs, {
            role: 'assistant',
            content: res.response,
            routedTo: res.routedTo,
            reasoning: res.reasoning,
            modelUsed: res.modelUsed,
            tokens: res.tokens,
            planId,
          }]);
          this.loading = false;
          this.scrollToBottom();
        },
        error: error => {
          this.handleChatError(error);
          this.loading = false;
        },
      });
    }
  }

  currentCliStatus(): CliProviderStatus | null {
    const provider = this.currentCliProvider();
    return provider ? this.cliProviderStatus()[provider] : null;
  }

  private refreshCliProviderStatus(selectedProvider?: CliProvider): void {
    this.jarvis.copilotProviderStatus().subscribe({
      next: statuses => {
        const mapped = statuses.reduce<Record<CliProvider, CliProviderStatus | null>>((acc, status) => {
          acc[status.provider] = status;
          return acc;
        }, {
          copilot: null,
          claude: null,
        });

        this.cliProviderStatus.set(mapped);

        if (selectedProvider) {
          const selected = mapped[selectedProvider];
          if (selected && (!selected.available || !selected.authenticated)) {
            this.uiError = `${selectedProvider.toUpperCase()} CLI: ${selected.message}`;
          }
        }
      },
      error: () => {
        this.uiError = 'No se pudo validar el estado de autenticación de los CLI.';
      }
    });
  }

  private handleChatError(error: unknown): void {
    const message = this.extractErrorMessage(error);
    this.uiError = message;
    this.messages.update(msgs => [...msgs, { role: 'assistant', content: `Error: ${message}` }]);
    this.scrollToBottom();
    const provider = this.currentCliProvider();
    if (provider) {
      this.refreshCliProviderStatus(provider);
    }
  }

  private ensureConnectionReady(): boolean {
    const provider = this.currentCliProvider();
    if (!provider) {
      return true;
    }

    const status = this.cliProviderStatus()[provider];
    if (!status) {
      this.uiError = 'Estado del CLI aún no disponible. Pulsa de nuevo en el tipo de conexión.';
      return false;
    }
    if (!status.available || !status.authenticated) {
      this.uiError = status.message;
      return false;
    }
    return true;
  }

  private resolveModelProvider(): 'ANTHROPIC' | 'AZURE' | 'OLLAMA' | 'CLAUDE_CLI' | 'COPILOT_CLI' {
    return this.currentConnectionCapabilities().provider || 'ANTHROPIC';
  }

  currentConnectionCapabilities(): ConnectionCapabilities {
    return this.connectionCapabilities[this.connectionType];
  }

  modelOptionsForCurrentConnection(): string[] {
    return this.currentConnectionCapabilities().models;
  }

  selectedModelForCurrentConnection(): string {
    return this.selectedModels[this.connectionType];
  }

  setSelectedModel(model: string): void {
    this.selectedModels = {
      ...this.selectedModels,
      [this.connectionType]: model,
    };
  }

  private syncHeuristicModeWithConnection(connectionType: ConnectionType): void {
    const capabilities = this.connectionCapabilities[connectionType];
    if (capabilities?.heuristicRequired) {
      this.heuristicRoutingEnabled = true;
    } else if (!capabilities?.heuristicSupported) {
      this.heuristicRoutingEnabled = false;
    }
  }

  private syncSelectedModelsWithCapabilities(): void {
    const updatedSelections = { ...this.selectedModels };
    (Object.keys(this.connectionCapabilities) as ConnectionType[]).forEach(connection => {
      const models = this.connectionCapabilities[connection]?.models ?? [];
      if (models.length === 0) {
        return;
      }
      if (!models.includes(updatedSelections[connection])) {
        updatedSelections[connection] = models[0];
      }
    });
    this.selectedModels = updatedSelections;
  }

  private resolveDefaultConnectionType(defaultProvider: string): ConnectionType {
    switch (defaultProvider) {
      case 'AZURE':
      case 'ANTHROPIC':
        return 'api';
      case 'OLLAMA':
        return 'ollama';
      case 'CLAUDE_CLI':
        return 'claude-cli';
      case 'COPILOT_CLI':
        return 'copilot-cli';
      default:
        return 'api';
    }
  }

  private supportsHeuristicRouting(): boolean {
    return this.currentConnectionCapabilities().heuristicSupported;
  }

  private currentCliProvider(): CliProvider | null {
    if (this.connectionType === 'claude-cli') {
      return 'claude';
    }
    if (this.connectionType === 'copilot-cli') {
      return 'copilot';
    }
    return null;
  }

  private extractErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (typeof error.error === 'string' && error.error.trim()) {
        return error.error;
      }
      if (typeof error.error === 'object' && error.error !== null) {
        const payload = error.error as { message?: string; error?: string; detail?: string };
        if (payload.message || payload.error || payload.detail) {
          return payload.message || payload.error || payload.detail || 'Error inesperado';
        }
      }
      return error.message || 'Error inesperado';
    }

    if (error instanceof Error) {
      return error.message;
    }

    return 'Error inesperado al ejecutar el CLI';
  }

  newChat(): void {
    this.messages.set([]);
    this.conversationId = this.newConversationId();
    this.googleConnected = false;
    this.googleStatusLabel = 'Not connected';
    this.refreshGoogleStatus();
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

  formatTime(date: Date): string {
    return date.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private buildPlan(userMessage: string, content: string, agentType?: AgentType): Plan {
    return {
      id: crypto.randomUUID(),
      title: this.extractTitle(content, userMessage),
      content,
      agentType,
      userMessage,
      timestamp: new Date(),
    };
  }

  private extractTitle(content: string, fallback: string): string {
    const firstLine = content.split('\n').find(l => l.trim().length > 3)?.trim() ?? '';
    const candidate = firstLine.replace(/^#+\s*/, '').replace(/^\*+\s*/, '');
    const source = candidate.length > 8 ? candidate : fallback;
    return source.length > 64 ? source.slice(0, 61) + '…' : source;
  }

  private newConversationId(): string {
    return crypto.randomUUID();
  }

  private scrollToBottom(): void {
    setTimeout(() => this.messagesEnd?.nativeElement.scrollIntoView({ behavior: 'smooth' }), 50);
  }
}
