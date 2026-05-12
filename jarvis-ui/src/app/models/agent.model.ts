export type AgentType = 'DIRECTOR' | 'SECRETARY' | 'SOCIAL_MEDIA' | 'DEVELOPER' | 'DEVOPS' | 'FRONTEND';

export interface TokenMetadata {
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  timestamp?: number;
}

export interface AgentRequest {
  message: string;
  conversationId: string;
  model?: string;
}

export interface AgentResponse {
  routedTo: AgentType;
  reasoning: string;
  response: string;
  modelUsed?: string;
  tokens?: TokenMetadata;
}

export interface BackendCapabilities {
  defaultProvider: string;
  cliMode: boolean;
  allowedModels: string[];
}

export interface GoogleAuthUrlResponse {
  conversationId: string;
  authorizationUrl: string;
}

export interface GoogleConnectionStatus {
  connected: boolean;
  conversationId: string;
  expiresAt?: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  routedTo?: AgentType;
  reasoning?: string;
  modelUsed?: string;
  tokens?: TokenMetadata;
  streaming?: boolean;
}

export const AGENT_META: Record<AgentType, { label: string; color: string; icon: string; skills: string }> = {
  DIRECTOR: {
    label: 'Director',
    color: '#14b8a6',
    icon: '🧠',
    skills: 'Understands intent, routes requests, and coordinates the best specialist for each task.'
  },
  SECRETARY: {
    label: 'Secretary',
    color: '#f59e0b',
    icon: '📋',
    skills: 'Manages agenda, calendar, meetings, documents (Drive), emails (Gmail), tasks and coordination.'
  },
  DEVELOPER: {
    label: 'Developer',
    color: '#3b82f6',
    icon: '💻',
    skills: 'Designs features, writes code, fixes bugs, and improves architecture and quality.'
  },
  DEVOPS: {
    label: 'DevOps',
    color: '#f97316',
    icon: '⚙️',
    skills: 'Builds CI/CD pipelines, manages infrastructure, and improves reliability and observability.'
  },
  SOCIAL_MEDIA: {
    label: 'Social Media',
    color: '#22c55e',
    icon: '📱',
    skills: 'Creates post ideas, optimizes tone and engagement, and adapts content by channel.'
  },
  FRONTEND: {
    label: 'Frontend',
    color: '#a855f7',
    icon: '🎨',
    skills: 'Builds UI components, improves UX, and keeps design systems consistent and accessible.'
  },
};
