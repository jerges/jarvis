import json
import os
import time
from typing import Optional

import boto3
from boto3.dynamodb.conditions import Key

_dynamodb = None
_table = None

MAX_MESSAGES = 20


def _get_table():
    global _dynamodb, _table
    if _table is None:
        _dynamodb = boto3.resource("dynamodb", region_name=os.environ["AWS_REGION"])
        _table = _dynamodb.Table(os.environ["DYNAMODB_TABLE"])
    return _table


def get_history(conversation_id: str) -> list[dict]:
    table = _get_table()
    response = table.query(
        KeyConditionExpression=Key("conversation_id").eq(conversation_id),
        ScanIndexForward=True,
        Limit=MAX_MESSAGES,
    )
    items = response.get("Items", [])
    return [{"role": item["role"], "content": item["content"]} for item in items]


def save_message(conversation_id: str, role: str, content: str) -> None:
    table = _get_table()
    ts = int(time.time() * 1000)
    table.put_item(
        Item={
            "conversation_id": conversation_id,
            "timestamp": ts,
            "role": role,
            "content": content,
            "ttl": int(time.time()) + 86400 * 7,  # 7-day TTL
        }
    )
    _prune_history(conversation_id)


def _prune_history(conversation_id: str) -> None:
    table = _get_table()
    response = table.query(
        KeyConditionExpression=Key("conversation_id").eq(conversation_id),
        ScanIndexForward=True,
    )
    items = response.get("Items", [])
    if len(items) > MAX_MESSAGES:
        excess = items[: len(items) - MAX_MESSAGES]
        with table.batch_writer() as batch:
            for item in excess:
                batch.delete_item(
                    Key={
                        "conversation_id": item["conversation_id"],
                        "timestamp": item["timestamp"],
                    }
                )
