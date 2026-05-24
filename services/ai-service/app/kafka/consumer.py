"""
Kafka consumer for algoverse.hints.requested events.

When a user requests a hint via the Kafka topic (fired from execution-service
after a failed submission), this consumer generates the hint asynchronously
and could publish results to a notification topic.

Event schema (HintRequestedPayload from libs/shared-types):
  {
    "eventType": "HINT_REQUESTED",
    "userId":     "<uuid>",
    "problemId":  "<uuid>",
    "hintLevel":  1-5,
    "language":   "<string>",
    "code":       "<string | null>"
  }
"""

import asyncio
import json
import logging
import uuid

from aiokafka import AIOKafkaConsumer
from aiokafka.errors import KafkaConnectionError

from app.config import get_settings
from app.database import AsyncSessionLocal
from app.services.hint_service import generate_hint

logger = logging.getLogger(__name__)

HINTS_TOPIC = "algoverse.hints.requested"
DLQ_TOPIC = "algoverse.hints.requested.dlq"


async def start_hints_consumer() -> None:
    settings = get_settings()
    consumer = AIOKafkaConsumer(
        HINTS_TOPIC,
        bootstrap_servers=settings.kafka_bootstrap_servers,
        group_id=settings.kafka_consumer_group,
        value_deserializer=lambda v: json.loads(v.decode("utf-8")),
        auto_offset_reset="earliest",
        enable_auto_commit=False,
        max_poll_interval_ms=300_000,
    )

    while True:
        try:
            await consumer.start()
            logger.info("Kafka consumer started — listening on %s", HINTS_TOPIC)
            break
        except KafkaConnectionError:
            logger.warning("Kafka not reachable, retrying in 5s…")
            await asyncio.sleep(5)

    try:
        async for msg in consumer:
            await _handle_message(msg, settings)
            await consumer.commit()
    except asyncio.CancelledError:
        logger.info("Kafka consumer shutting down")
    finally:
        await consumer.stop()


async def _handle_message(msg, settings) -> None:
    event = msg.value
    event_type = event.get("eventType", "")

    if event_type != "HINT_REQUESTED":
        return

    try:
        user_id = uuid.UUID(event["userId"])
        problem_id = uuid.UUID(event["problemId"])
        hint_level = int(event.get("hintLevel", 1))
        language = event.get("language", "python3")
        code = event.get("code")

        async with AsyncSessionLocal() as db:
            await generate_hint(
                db=db,
                settings=settings,
                user_id=user_id,
                problem_id=problem_id,
                submission_id=None,
                hint_level=hint_level,
                current_code=code,
                language=language,
            )
            await db.commit()
            logger.info("Processed hint for user=%s problem=%s level=%d", user_id, problem_id, hint_level)

    except Exception:
        logger.exception("Failed to process hint event: %s", event)
        # Message is not re-committed — will be retried on next poll
