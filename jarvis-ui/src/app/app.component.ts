import { Component, ElementRef, ViewChild, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { JarvisService } from './services/jarvis.service';
import { AgentType, AppMode, ChatMessage, Plan, AGENT_META } from './models/agent.model';

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
    if (mode === 'agent') this.selectedPlanId.set(null);
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

    const request = { message: text, conversationId: this.conversationId };

    if (this.streamMode) {
      const assistantMsg: ChatMessage = { role: 'assistant', content: '', streaming: true };
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
            const finished = { ...updated[updated.length - 1], streaming: false };
            // In plan mode, register the streamed content as a plan
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
          }]);
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
