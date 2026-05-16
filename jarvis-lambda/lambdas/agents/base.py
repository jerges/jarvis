"""Base class for all specialized agents."""
import sys

sys.path.insert(0, "/opt/python")

from shared.anthropic_client import chat, AGENT_MODEL


class BaseAgent:
    """
    Subclasses define `system_prompt`.
    handler() is the Lambda entry point.
    """

    system_prompt: str = ""
    model: str = AGENT_MODEL
    max_tokens: int = 4096

    def process(self, message: str, history: list[dict]) -> str:
        messages = history[-10:] + [{"role": "user", "content": message}]
        response, _ = chat(
            system=self.system_prompt,
            messages=messages,
            model=self.model,
            max_tokens=self.max_tokens,
        )
        return response


def make_handler(agent_class):
    """Factory that returns a Lambda handler function for an agent class."""
    agent = agent_class()

    def handler(event: dict, context) -> dict:
        conversation_id = event.get("conversation_id", "unknown")
        message = event.get("message", "")
        history = event.get("history", [])
        response = agent.process(message, history)
        return {"conversation_id": conversation_id, "response": response}

    return handler
