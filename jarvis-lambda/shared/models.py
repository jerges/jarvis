from dataclasses import dataclass, field
from enum import Enum
from typing import Optional


class AgentType(str, Enum):
    JARVIS = "JARVIS"
    SECRETARY = "SECRETARY"
    DEVELOPER = "DEVELOPER"
    DEVOPS = "DEVOPS"
    FRONTEND = "FRONTEND"
    SECURITY = "SECURITY"
    SOCIAL_MEDIA = "SOCIAL_MEDIA"


@dataclass
class AgentRequest:
    message: str
    conversation_id: str
    source: str = "whatsapp"
    sender: Optional[str] = None


@dataclass
class RoutingDecision:
    agent_type: AgentType
    reasoning: str


@dataclass
class TokenUsage:
    input_tokens: int
    output_tokens: int
    total_tokens: int


@dataclass
class AgentResponse:
    routed_to: AgentType
    reasoning: str
    response: str
    model_used: str
    tokens: Optional[TokenUsage] = None
