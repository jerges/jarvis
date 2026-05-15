import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { shareReplay } from 'rxjs/operators';
import {
  AgentRequest,
  AgentResponse,
  AuthUser,
  BackendCapabilities,
  CliProviderStatus,
  CopilotRequest,
  CopilotResponse,
  GoogleAuthUrlResponse,
  GoogleConnectionStatus,
} from '../models/agent.model';

@Injectable({ providedIn: 'root' })
export class JarvisService {
  private readonly base = '/api/jarvis';
  private readonly googleBase = '/api/google';
  private capabilitiesCache$?: Observable<BackendCapabilities>;

  constructor(private http: HttpClient) {}

  chat(request: AgentRequest): Observable<AgentResponse> {
    return this.http.post<AgentResponse>(`${this.base}/chat`, request);
  }

  capabilities(): Observable<BackendCapabilities> {
    if (!this.capabilitiesCache$) {
      this.capabilitiesCache$ = this.http
        .get<BackendCapabilities>(`${this.base}/capabilities`)
        .pipe(shareReplay({ bufferSize: 1, refCount: false }));
    }
    return this.capabilitiesCache$;
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

  checkAuth(): Observable<AuthUser> {
    return this.http.get<AuthUser>('/api/auth/me');
  }

  getLoginUrl(): Observable<{ loginUrl: string }> {
    return this.http.get<{ loginUrl: string }>('/api/auth/login-url');
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {});
  }

  copilotChat(request: CopilotRequest): Observable<CopilotResponse> {
    return this.http.post<CopilotResponse>(`${this.base}/copilot`, request);
  }

  copilotStream(request: CopilotRequest): Observable<string> {
    return this.fetchStream(`${this.base}/copilot/stream`, request);
  }

  copilotProviderStatus(): Observable<CliProviderStatus[]> {
    return this.http.get<CliProviderStatus[]>(`${this.base}/copilot/providers`);
  }

  stream(request: AgentRequest): Observable<string> {
    return this.fetchStream(`${this.base}/stream`, request);
  }

  private fetchStream(url: string, body: object): Observable<string> {
    return new Observable(subscriber => {
      const controller = new AbortController();

      fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        signal: controller.signal,
      }).then(async response => {
        if (!response.ok) {
          const errorPayload = await response.text();
          const detail = this.extractBackendErrorMessage(errorPayload);
          throw new Error(detail || `Streaming request failed with status ${response.status}`);
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

  private extractBackendErrorMessage(payload: string): string {
    if (!payload) {
      return '';
    }

    try {
      const parsed = JSON.parse(payload) as { message?: string; error?: string; detail?: string };
      return parsed.message || parsed.error || parsed.detail || payload;
    } catch {
      return payload;
    }
  }
}
