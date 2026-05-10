import { Component, ElementRef, ViewChild, signal, computed } from '@angular/core';
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
export class AppComponent {
  @ViewChild('messagesEnd') messagesEnd!: ElementRef;

  message = '';
  conversationId = this.newConversationId();
  streamMode = false;
  loading = false;

  messages = signal<ChatMessage[]>([]);
  agentMeta = AGENT_META;

  agentColor = computed(() => {
    const last = this.messages().findLast(m => m.routedTo);
    return last?.routedTo ? AGENT_META[last.routedTo].color : '#6366f1';
  });

  constructor(private jarvis: JarvisService) {}

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
