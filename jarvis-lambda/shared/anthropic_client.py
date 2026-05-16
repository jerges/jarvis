import json
import os
from functools import lru_cache
from typing import Optional

import boto3
import anthropic

_secrets_client = None

ORCHESTRATOR_MODEL = "claude-haiku-4-5-20251001"          # routing: cheap classification, ~20x less than Opus
ORCHESTRATOR_FALLBACK_MODEL = "claude-sonnet-4-6-20250514"  # fallback if Haiku fails or returns invalid JSON
AGENT_MODEL = "claude-sonnet-4-6-20250514"


def _get_secrets_client():
    global _secrets_client
    if _secrets_client is None:
        _secrets_client = boto3.client("secretsmanager", region_name=os.environ["AWS_REGION"])
    return _secrets_client


@lru_cache(maxsize=1)
def _get_api_key() -> str:
    secret_arn = os.environ["ANTHROPIC_SECRET_ARN"]
    client = _get_secrets_client()
    response = client.get_secret_value(SecretId=secret_arn)
    secret = json.loads(response["SecretString"])
    return secret["api_key"]


def get_client() -> anthropic.Anthropic:
    return anthropic.Anthropic(api_key=_get_api_key())


def chat(
    system: str,
    messages: list[dict],
    model: str = AGENT_MODEL,
    max_tokens: int = 4096,
) -> tuple[str, dict]:
    client = get_client()
    response = client.messages.create(
        model=model,
        max_tokens=max_tokens,
        system=system,
        messages=messages,
    )
    text = response.content[0].text
    usage = {
        "input_tokens": response.usage.input_tokens,
        "output_tokens": response.usage.output_tokens,
        "total_tokens": response.usage.input_tokens + response.usage.output_tokens,
    }
    return text, usage
