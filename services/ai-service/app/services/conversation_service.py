"""
Conversation service — manages multi-turn AI tutor threads with SSE streaming.
"""

import json
import uuid
from collections.abc import AsyncGenerator
from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.ai_conversation import AiConversation
from app.prompts.conversation_prompt import TUTOR_SYSTEM_PROMPT
from app.services.claude_client import stream_message


async def stream_tutor_response(
    db: AsyncSession,
    user_id: uuid.UUID,
    problem_id: uuid.UUID | None,
    message: str,
    thread_id: str | None,
    current_code: str | None,
    language: str | None,
    hint_level: int,
) -> AsyncGenerator[str, None]:
    """
    Streams SSE events from the AI tutor.

    Event types:
      metadata — {conversationId, threadId, model}
      delta    — {text: "<chunk>"}
      done     — {fullText: "<complete>", tokenUsage: {...}}
      error    — {code, message}
    """
    # Load or create the conversation thread
    conversation = await _get_or_create_conversation(
        db, user_id, problem_id, thread_id
    )
    conv_id = str(conversation.id)
    new_thread_id = conversation.thread_id or conv_id

    # Yield metadata event
    metadata_payload = json.dumps({
        "conversationId": conv_id,
        "threadId": new_thread_id,
        "model": "claude-sonnet-4-6",
    })
    yield f"id: evt_meta\nevent: metadata\ndata: {metadata_payload}\n\n"

    # Build history from stored messages
    stored = conversation.messages or []
    history = [{"role": m["role"], "content": m["content"]} for m in stored[-20:]]

    # Append current user turn
    context_block = _build_context_block(problem_id, current_code, language, hint_level)
    history.append({"role": "user", "content": message})

    # Stream Claude response
    full_text = ""
    event_idx = 0
    try:
        async for chunk in stream_message(
            system_prompt=TUTOR_SYSTEM_PROMPT,
            messages=history,
            context_block=context_block,
            max_tokens=1500,
        ):
            full_text += chunk
            event_idx += 1
            delta_payload = json.dumps({"text": chunk})
            yield f"id: evt_{event_idx:04d}\nevent: delta\ndata: {delta_payload}\n\n"

        # Persist the exchange
        now = datetime.now(timezone.utc).isoformat()
        conversation.messages = stored + [
            {"role": "user", "content": message, "created_at": now, "tokens": 0},
            {"role": "assistant", "content": full_text, "created_at": now, "tokens": 0},
        ]
        conversation.updated_at = datetime.now(timezone.utc)
        await db.flush()

        done_payload = json.dumps({"fullText": full_text})
        yield f"id: evt_done\nevent: done\ndata: {done_payload}\n\n"

    except Exception as exc:
        err_payload = json.dumps({"code": "STREAM_ERROR", "message": str(exc)})
        yield f"id: evt_err\nevent: error\ndata: {err_payload}\n\n"


async def list_conversations(
    db: AsyncSession,
    user_id: uuid.UUID,
) -> list[AiConversation]:
    result = await db.execute(
        select(AiConversation)
        .where(AiConversation.user_id == user_id)
        .order_by(AiConversation.updated_at.desc())
        .limit(50)
    )
    return list(result.scalars().all())


async def _get_or_create_conversation(
    db: AsyncSession,
    user_id: uuid.UUID,
    problem_id: uuid.UUID | None,
    thread_id: str | None,
) -> AiConversation:
    if thread_id:
        result = await db.execute(
            select(AiConversation).where(
                AiConversation.user_id == user_id,
                AiConversation.thread_id == thread_id,
            )
        )
        existing = result.scalar_one_or_none()
        if existing:
            return existing

    conv = AiConversation(
        user_id=user_id,
        problem_id=problem_id,
        thread_id=thread_id,
        messages=[],
    )
    db.add(conv)
    await db.flush()
    if not conv.thread_id:
        conv.thread_id = str(conv.id)
    return conv


def _build_context_block(
    problem_id: uuid.UUID | None,
    current_code: str | None,
    language: str | None,
    hint_level: int,
) -> str | None:
    parts = []
    if problem_id:
        parts.append(f"Problem ID: {problem_id}")
    if current_code and language:
        parts.append(f"User's current code ({language}):\n```{language}\n{current_code}\n```")
    if hint_level:
        parts.append(f"Hints already revealed: {hint_level}/5")
    return "\n\n".join(parts) if parts else None
