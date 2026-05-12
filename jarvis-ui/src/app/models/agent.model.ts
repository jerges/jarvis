export type AgentType = 'SOCIAL_MEDIA' | 'DEVELOPER' | 'DEVOPS' | 'FRONTEND' | 'SECURITY';
export type AppMode   = 'agent' | 'plan' | 'cli' | 'copilot';
export type CopilotMode   = 'suggest' | 'explain';
export type CopilotTarget = 'shell' | 'git' | 'gh';

export interface AgentRequest {
  message: string;
  conversationId: string;
}

export interface AgentResponse {
  routedTo: AgentType;
  reasoning: string;
  response: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  routedTo?: AgentType;
  reasoning?: string;
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

export interface CliRequest {
  message: string;
  sessionId?: string;
  model?: string;
}

export interface CliResponse {
  response: string;
  sessionId: string | null;
  costUsd: number;
}

export interface CopilotRequest {
  message: string;
  mode: CopilotMode;
  target?: CopilotTarget;
}

export interface CopilotResponse {
  response: string;
  mode: string;
}

export const AGENT_META: Record<AgentType, { label: string; color: string; icon: string }> = {
  DEVELOPER:    { label: 'Developer',     color: '#3b82f6', icon: '💻' },
  DEVOPS:       { label: 'DevOps',        color: '#f97316', icon: '⚙️' },
  SOCIAL_MEDIA: { label: 'Social Media',  color: '#22c55e', icon: '📱' },
  FRONTEND:     { label: 'Frontend',      color: '#a855f7', icon: '🎨' },
  SECURITY:     { label: 'Security',      color: '#ef4444', icon: '🔐' },
};
