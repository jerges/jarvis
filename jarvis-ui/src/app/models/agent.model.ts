export type AgentType = 'JARVIS' | 'SECRETARY' | 'SOCIAL_MEDIA' | 'DEVELOPER' | 'DEVOPS' | 'FRONTEND' | 'SECURITY';

export type AppMode = 'agent' | 'plan';

export type CopilotMode = 'suggest' | 'explain';
export type CopilotTarget = 'shell' | 'git' | 'gh';
export type CliProvider = 'copilot' | 'claude';
export type ConnectionType = 'api' | 'ollama' | 'copilot-cli' | 'claude-cli';

export interface TokenMetadata {
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  timestamp?: number;
}

export interface AgentRequest {
  message: string;
  conversationId: string;
  provider?: 'ANTHROPIC' | 'AZURE' | 'OLLAMA' | 'CLAUDE_CLI' | 'COPILOT_CLI';
  model?: string;
  heuristicRoutingEnabled?: boolean;
  heuristicMinKeywordHits?: number;
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
  connections: Record<ConnectionType, ConnectionCapabilities>;
}

export interface ConnectionCapabilities {
  label: string;
  models: string[];
  provider?: 'ANTHROPIC' | 'AZURE' | 'OLLAMA' | 'CLAUDE_CLI' | 'COPILOT_CLI';
  heuristicSupported: boolean;
  heuristicRequired: boolean;
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

export interface CopilotRequest {
  message: string;
  mode: CopilotMode;
  target?: CopilotTarget;
  provider: CliProvider;
}

export interface CopilotResponse {
  response: string;
  mode: string;
}

export interface CliProviderStatus {
  provider: CliProvider;
  available: boolean;
  authenticated: boolean;
  message: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  routedTo?: AgentType;
  reasoning?: string;
  modelUsed?: string;
  tokens?: TokenMetadata;
  streaming?: boolean;
  planId?: string;
}

export interface Plan {
  id: string;
  title: string;
  content: string;
  agentType?: AgentType;
  userMessage: string;
  timestamp: Date;
}

export const AGENT_META: Record<AgentType, { label: string; color: string; icon: string; skills: string }> = {
  JARVIS: {
    label: 'Jarvis',
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
  SECURITY: {
    label: 'Security',
    color: '#ef4444',
    icon: '🔐',
    skills: 'Expert in cybersecurity, pentesting, OWASP, CVEs, threat modeling, hardening and DevSecOps.'
  },
};
