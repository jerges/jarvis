import { Component, ElementRef, ViewChild, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { JarvisService } from './services/jarvis.service';
import { AgentType, AppMode, ChatMessage, CliRequest, CliResponse, CopilotMode, CopilotRequest, CopilotResponse, CopilotTarget, Plan, AGENT_META } from './models/agent.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  @ViewChild('messagesEnd') messagesEnd!: ElementRef;

  message      = '';
  conversationId = this.newConversationId();
  streamMode   = false;
  loading      = false;

  messages       = signal<ChatMessage[]>([]);
  plans          = signal<Plan[]>([]);
  selectedPlanId = signal<string | null>(null);
  appMode        = signal<AppMode>('agent');

  // Estado del modo CLI
  cliSessionId: string | null = null;
  cliModel = 'sonnet';
  lastCliCostUsd = 0;

  // Estado del modo Copilot
  copilotMode: CopilotMode   = 'suggest';
  copilotTarget: CopilotTarget = 'shell';

  agentMeta = AGENT_META;

  agentColor = computed(() => {
    const last = this.messages().findLast(m => m.routedTo);
    return last?.routedTo ? AGENT_META[last.routedTo].color : '#6366f1';
  });

  selectedPlan = computed(() => {
    const id = this.selectedPlanId();
    return id ? this.plans().find(p => p.id === id) ?? null : null;
  });

  planCount = computed(() => this.plans().length);

  constructor(private jarvis: JarvisService) {}

  // ── Mode switching ────────────────────────────────────────────────────────

  setMode(mode: AppMode): void {
    this.appMode.set(mode);
    if (mode !== 'plan') this.selectedPlanId.set(null);
  }

  resetCliSession(): void {
    this.cliSessionId = null;
    this.lastCliCostUsd = 0;
    this.messages.set([]);
    this.conversationId = this.newConversationId();
  }

  // ── Plan selection ────────────────────────────────────────────────────────

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

  // ── Sending messages ──────────────────────────────────────────────────────

  send(): void {
    const text = this.message.trim();
    if (!text || this.loading) return;

    this.messages.update(msgs => [...msgs, { role: 'user', content: text }]);
    this.message = '';
    this.loading = true;
    this.scrollToBottom();

    // ── Modo CLI ──────────────────────────────────────────────────────────────
    if (this.appMode() === 'cli') {
      this.sendCli(text);
      return;
    }

    // ── Modo Copilot ──────────────────────────────────────────────────────────
    if (this.appMode() === 'copilot') {
      this.sendCopilot(text);
      return;
    }

    const request = { message: text, conversationId: this.conversationId };

    if (this.streamMode) {
      const assistantMsg: ChatMessage = { role: 'assistant', content: '', streaming: true };
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
                  provider: meta.provider,
                  model: meta.model,
                };
                return updated;
              });
            } catch { /* ignorar parse errors */ }
          } else if (token.startsWith('[USAGE] ')) {
            try {
              const usage = JSON.parse(token.slice(8));
              this.messages.update(msgs => {
                const updated = [...msgs];
                updated[updated.length - 1] = {
                  ...updated[updated.length - 1],
                  inputTokens: usage.inputTokens,
                  outputTokens: usage.outputTokens,
                };
                return updated;
              });
            } catch { /* ignorar parse errors */ }
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
        error: () => (this.loading = false),
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
            planId,
            provider: res.provider,
            model: res.model,
            inputTokens: res.inputTokens,
            outputTokens: res.outputTokens,
          }]);
          this.loading = false;
          this.scrollToBottom();
        },
        error: () => (this.loading = false),
      });
    }
  }

  private sendCli(text: string): void {
    const cliReq: CliRequest = {
      message: text,
      sessionId: this.cliSessionId ?? undefined,
      model: this.cliModel,
    };

    if (this.streamMode) {
      const assistantMsg: ChatMessage = { role: 'assistant', content: '', streaming: true };
      this.messages.update(msgs => [...msgs, assistantMsg]);

      let buffer = '';
      this.jarvis.cliStream(cliReq).subscribe({
        next: token => {
          if (token.startsWith('[DONE] ')) {
            // Parsear metadata del evento de finalización
            try {
              const meta = JSON.parse(token.slice(7));
              if (meta.sessionId) this.cliSessionId = meta.sessionId;
              this.lastCliCostUsd += meta.costUsd ?? 0;
            } catch { /* ignorar parse errors */ }
          } else {
            buffer += token;
            this.messages.update(msgs => {
              const updated = [...msgs];
              updated[updated.length - 1] = { ...updated[updated.length - 1], content: buffer };
              return updated;
            });
            this.scrollToBottom();
          }
        },
        complete: () => {
          this.messages.update(msgs => {
            const updated = [...msgs];
            updated[updated.length - 1] = { ...updated[updated.length - 1], streaming: false };
            return updated;
          });
          this.loading = false;
        },
        error: () => (this.loading = false),
      });
    } else {
      this.jarvis.cliChat(cliReq).subscribe({
        next: (res: CliResponse) => {
          if (res.sessionId) this.cliSessionId = res.sessionId;
          this.lastCliCostUsd += res.costUsd ?? 0;
          this.messages.update(msgs => [...msgs, {
            role: 'assistant',
            content: res.response,
          }]);
          this.loading = false;
          this.scrollToBottom();
        },
        error: () => (this.loading = false),
      });
    }
  }

  private sendCopilot(text: string): void {
    const req: CopilotRequest = {
      message: text,
      mode: this.copilotMode,
      target: this.copilotMode === 'suggest' ? this.copilotTarget : undefined,
    };

    if (this.streamMode) {
      const assistantMsg: ChatMessage = { role: 'assistant', content: '', streaming: true };
      this.messages.update(msgs => [...msgs, assistantMsg]);

      let buffer = '';
      this.jarvis.copilotStream(req).subscribe({
        next: token => {
          if (token.startsWith('[DONE]')) return;
          buffer += token;
          this.messages.update(msgs => {
            const updated = [...msgs];
            updated[updated.length - 1] = { ...updated[updated.length - 1], content: buffer };
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
        error: () => (this.loading = false),
      });
    } else {
      this.jarvis.copilotChat(req).subscribe({
        next: (res: CopilotResponse) => {
          this.messages.update(msgs => [...msgs, { role: 'assistant', content: res.response }]);
          this.loading = false;
          this.scrollToBottom();
        },
        error: () => (this.loading = false),
      });
    }
  }

  newChat(): void {
    this.messages.set([]);
    this.conversationId = this.newConversationId();
    // Resetear sesión CLI al iniciar nueva conversación
    if (this.appMode() === 'cli') {
      this.cliSessionId = null;
      this.lastCliCostUsd = 0;
    }
  }

  clearPlans(): void {
    this.plans.set([]);
    this.selectedPlanId.set(null);
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
