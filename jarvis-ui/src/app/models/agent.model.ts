export type AgentType = 'SOCIAL_MEDIA' | 'DEVELOPER' | 'DEVOPS' | 'FRONTEND';

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
}

export const AGENT_META: Record<AgentType, { label: string; color: string; icon: string }> = {
  DEVELOPER:    { label: 'Developer',     color: '#3b82f6', icon: '💻' },
  DEVOPS:       { label: 'DevOps',        color: '#f97316', icon: '⚙️' },
  SOCIAL_MEDIA: { label: 'Social Media',  color: '#22c55e', icon: '📱' },
  FRONTEND:     { label: 'Frontend',      color: '#a855f7', icon: '🎨' },
};
