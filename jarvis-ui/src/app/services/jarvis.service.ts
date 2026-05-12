import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AgentRequest,
  AgentResponse,
  BackendCapabilities,
  GoogleAuthUrlResponse,
  GoogleConnectionStatus,
} from '../models/agent.model';

@Injectable({ providedIn: 'root' })
export class JarvisService {
  private readonly base = '/api/jarvis';
  private readonly googleBase = '/api/google';

  constructor(private http: HttpClient) {}

  chat(request: AgentRequest): Observable<AgentResponse> {
    return this.http.post<AgentResponse>(`${this.base}/chat`, request);
  }

  capabilities(): Observable<BackendCapabilities> {
    return this.http.get<BackendCapabilities>(`${this.base}/capabilities`);
  }

  googleAuthUrl(conversationId: string): Observable<GoogleAuthUrlResponse> {
    return this.http.get<GoogleAuthUrlResponse>(`${this.googleBase}/oauth/url`, {
      params: { conversationId },
    });
  }

  googleConnectionStatus(conversationId: string): Observable<GoogleConnectionStatus> {
    return this.http.get<GoogleConnectionStatus>(`${this.googleBase}/status`, {
      params: { conversationId },
    });
  }

  stream(request: AgentRequest): Observable<string> {
    return new Observable(subscriber => {
      const controller = new AbortController();

      fetch(`${this.base}/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
        signal: controller.signal,
      }).then(async response => {
        if (!response.ok) {
          throw new Error(`Streaming request failed with status ${response.status}`);
        }
        if (!response.body) {
          throw new Error('Streaming response has no body');
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        const emitEvent = (eventBlock: string) => {
          const lines = eventBlock.split('\n');
          const dataLines = lines
            .filter(line => line.startsWith('data:'))
            // Preserve payload as-is; removing spaces breaks tokenized LLM text.
            .map(line => line.slice(5));

          if (dataLines.length > 0) {
            subscriber.next(dataLines.join('\n'));
          }
        };

        while (true) {
          const { done, value } = await reader.read();
          if (done) {
            buffer += decoder.decode();
            if (buffer.length > 0) {
              emitEvent(buffer.replace(/\r/g, ''));
            }
            subscriber.complete();
            break;
          }

          buffer += decoder.decode(value, { stream: true });

          const normalized = buffer.replace(/\r/g, '');
          const events = normalized.split('\n\n');
          buffer = events.pop() ?? '';

          for (const eventBlock of events) {
            emitEvent(eventBlock);
          }
        }
      }).catch(err => {
        if (err.name !== 'AbortError') subscriber.error(err);
      });

      return () => controller.abort();
    });
  }
}
