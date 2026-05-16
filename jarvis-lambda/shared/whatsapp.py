import json
import os
from functools import lru_cache
from typing import Optional

import boto3
import urllib.request
import urllib.parse

_secrets_client = None
GRAPH_API_VERSION = "v21.0"
GRAPH_API_BASE = f"https://graph.facebook.com/{GRAPH_API_VERSION}"
MAX_CHUNK = 4000


def _get_secrets_client():
    global _secrets_client
    if _secrets_client is None:
        _secrets_client = boto3.client("secretsmanager", region_name=os.environ["AWS_REGION"])
    return _secrets_client


@lru_cache(maxsize=1)
def _get_whatsapp_config() -> dict:
    secret_arn = os.environ["WHATSAPP_SECRET_ARN"]
    client = _get_secrets_client()
    response = client.get_secret_value(SecretId=secret_arn)
    return json.loads(response["SecretString"])


def get_verify_token() -> str:
    return _get_whatsapp_config()["verify_token"]


def send_message(to: str, text: str) -> None:
    config = _get_whatsapp_config()
    phone_number_id = config["phone_number_id"]
    access_token = config["access_token"]
    url = f"{GRAPH_API_BASE}/{phone_number_id}/messages"

    chunks = _chunk_text(text)
    for chunk in chunks:
        payload = json.dumps(
            {
                "messaging_product": "whatsapp",
                "to": to,
                "type": "text",
                "text": {"body": chunk},
            }
        ).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=payload,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {access_token}",
            },
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            resp.read()


def send_typing_indicator(to: str) -> None:
    config = _get_whatsapp_config()
    phone_number_id = config["phone_number_id"]
    access_token = config["access_token"]
    url = f"{GRAPH_API_BASE}/{phone_number_id}/messages"
    payload = json.dumps(
        {
            "messaging_product": "whatsapp",
            "status": "read",
            "message_id": "placeholder",
        }
    ).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=payload,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {access_token}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            resp.read()
    except Exception:
        pass


def parse_incoming_message(payload: dict) -> Optional[tuple[str, str]]:
    """Extract (phone_number, message_text) from Meta webhook payload. Returns None if not a text message."""
    try:
        entry = payload["entry"][0]
        changes = entry["changes"][0]
        value = changes["value"]
        messages = value.get("messages", [])
        if not messages:
            return None
        msg = messages[0]
        if msg.get("type") != "text":
            return None
        phone = msg["from"]
        text = msg["text"]["body"]
        return phone, text
    except (KeyError, IndexError):
        return None


def _chunk_text(text: str) -> list[str]:
    if len(text) <= MAX_CHUNK:
        return [text]
    chunks = []
    while text:
        if len(text) <= MAX_CHUNK:
            chunks.append(text)
            break
        split_at = text.rfind("\n", 0, MAX_CHUNK)
        if split_at == -1:
            split_at = MAX_CHUNK
        chunks.append(text[:split_at])
        text = text[split_at:].lstrip("\n")
    return chunks
