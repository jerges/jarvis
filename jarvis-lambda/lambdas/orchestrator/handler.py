"""
Orchestrator Lambda
- Triggered by SQS (ORCHESTRATOR_QUEUE)
- Determines routing via Claude Opus
- Invokes specialized agent Lambda or responds directly (JARVIS type)
- Sends WhatsApp response
"""
import json
import os
import sys

sys.path.insert(0, "/opt/python")

import boto3

from shared.anthropic_client import chat, ORCHESTRATOR_MODEL, AGENT_MODEL
from shared.conversation import get_history, save_message
from shared.models import AgentType, RoutingDecision
from shared.whatsapp import send_message
from lambdas.orchestrator.prompts import ROUTING_SYSTEM, GENERALIST_SYSTEM

_lambda_client = None


def _get_lambda():
    global _lambda_client
    if _lambda_client is None:
        _lambda_client = boto3.client("lambda", region_name=os.environ["AWS_REGION"])
    return _lambda_client


AGENT_LAMBDA_MAP = {
    AgentType.SECRETARY: os.environ.get("LAMBDA_SECRETARY", "jarvis-agent-secretary"),
    AgentType.DEVELOPER: os.environ.get("LAMBDA_DEVELOPER", "jarvis-agent-developer"),
    AgentType.DEVOPS: os.environ.get("LAMBDA_DEVOPS", "jarvis-agent-devops"),
    AgentType.FRONTEND: os.environ.get("LAMBDA_FRONTEND", "jarvis-agent-frontend"),
    AgentType.SECURITY: os.environ.get("LAMBDA_SECURITY", "jarvis-agent-security"),
    AgentType.SOCIAL_MEDIA: os.environ.get("LAMBDA_SOCIAL_MEDIA", "jarvis-agent-social-media"),
}


def handler(event: dict, context) -> None:
    for record in event.get("Records", []):
        try:
            payload = json.loads(record["body"])
            _process(payload)
        except Exception as e:
            print(f"Orchestrator error: {e}")
            raise


def _process(payload: dict) -> None:
    conversation_id = payload["conversation_id"]
    message = payload["message"]
    sender = payload.get("sender")

    history = get_history(conversation_id)
    routing = _route(message, history)

    save_message(conversation_id, "user", message)

    if routing.agent_type == AgentType.JARVIS:
        response_text = _respond_directly(message, history)
    else:
        response_text = _invoke_agent(routing.agent_type, conversation_id, message, history)

    save_message(conversation_id, "assistant", response_text)

    if sender:
        send_message(sender, response_text)


def _route(message: str, history: list[dict]) -> RoutingDecision:
    context_snippet = ""
    if history:
        last = history[-3:]
        context_snippet = "\n".join(f"{m['role']}: {m['content'][:200]}" for m in last)
        context_snippet = f"\nContexto reciente:\n{context_snippet}\n"

    user_content = f"{context_snippet}Mensaje a clasificar: {message}"
    messages = [{"role": "user", "content": user_content}]

    raw, _ = chat(
        system=ROUTING_SYSTEM,
        messages=messages,
        model=ORCHESTRATOR_MODEL,
        max_tokens=256,
    )

    try:
        data = json.loads(raw.strip())
        agent_type = AgentType(data["agent"])
        reasoning = data.get("reasoning", "")
    except Exception:
        agent_type = AgentType.JARVIS
        reasoning = "Fallback a respuesta general"

    return RoutingDecision(agent_type=agent_type, reasoning=reasoning)


def _respond_directly(message: str, history: list[dict]) -> str:
    messages = history[-10:] + [{"role": "user", "content": message}]
    response, _ = chat(
        system=GENERALIST_SYSTEM,
        messages=messages,
        model=AGENT_MODEL,
        max_tokens=2048,
    )
    return response


def _invoke_agent(agent_type: AgentType, conversation_id: str, message: str, history: list[dict]) -> str:
    function_name = AGENT_LAMBDA_MAP.get(agent_type)
    if not function_name:
        return _respond_directly(message, history)

    payload = {
        "conversation_id": conversation_id,
        "message": message,
        "history": history[-10:],
    }

    response = _get_lambda().invoke(
        FunctionName=function_name,
        InvocationType="RequestResponse",
        Payload=json.dumps(payload),
    )
    result = json.loads(response["Payload"].read())

    if "errorMessage" in result:
        print(f"Agent {agent_type} error: {result['errorMessage']}")
        return _respond_directly(message, history)

    return result.get("response", "No pude obtener una respuesta del agente.")
