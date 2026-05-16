"""
WhatsApp Webhook Lambda
- GET: Meta verification challenge
- POST: Incoming message → SQS queue (returns 200 immediately)
"""
import json
import os
import sys

sys.path.insert(0, "/opt/python")  # Lambda layer with shared utilities

import boto3

from shared.whatsapp import get_verify_token, parse_incoming_message

_sqs = None


def _get_sqs():
    global _sqs
    if _sqs is None:
        _sqs = boto3.client("sqs", region_name=os.environ["AWS_REGION"])
    return _sqs


def handler(event: dict, context) -> dict:
    method = event.get("requestContext", {}).get("http", {}).get("method", "GET")

    if method == "GET":
        return _handle_verification(event)
    elif method == "POST":
        return _handle_message(event)

    return {"statusCode": 405, "body": "Method Not Allowed"}


def _handle_verification(event: dict) -> dict:
    params = event.get("queryStringParameters", {}) or {}
    mode = params.get("hub.mode")
    token = params.get("hub.verify_token")
    challenge = params.get("hub.challenge")

    if mode == "subscribe" and token == get_verify_token():
        return {"statusCode": 200, "body": challenge}

    return {"statusCode": 403, "body": "Forbidden"}


def _handle_message(event: dict) -> dict:
    try:
        body = json.loads(event.get("body", "{}"))
        result = parse_incoming_message(body)
        if result:
            phone, text = result
            conversation_id = f"whatsapp-{phone}"
            _enqueue(
                {
                    "conversation_id": conversation_id,
                    "message": text,
                    "sender": phone,
                    "source": "whatsapp",
                }
            )
    except Exception as e:
        print(f"Webhook error: {e}")

    # Always return 200 to Meta immediately
    return {"statusCode": 200, "body": "OK"}


def _enqueue(payload: dict) -> None:
    sqs = _get_sqs()
    sqs.send_message(
        QueueUrl=os.environ["ORCHESTRATOR_QUEUE_URL"],
        MessageBody=json.dumps(payload),
    )
