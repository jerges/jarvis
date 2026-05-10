import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AgentRequest, AgentResponse } from '../models/agent.model';

@Injectable({ providedIn: 'root' })
export class JarvisService {
  private readonly base = '/api/jarvis';

  constructor(private http: HttpClient) {}

  chat(request: AgentRequest): Observable<AgentResponse> {
    return this.http.post<AgentResponse>(`${this.base}/chat`, request);
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
        const reader = response.body!.getReader();
        const decoder = new TextDecoder();

        while (true) {
          const { done, value } = await reader.read();
          if (done) { subscriber.complete(); break; }

          const chunk = decoder.decode(value, { stream: true });
          for (const line of chunk.split('\n')) {
            if (line.startsWith('data:')) {
              const token = line.slice(5).trim();
              if (token) subscriber.next(token);
            }
          }
        }
      }).catch(err => {
        if (err.name !== 'AbortError') subscriber.error(err);
      });

      return () => controller.abort();
    });
  }
}
