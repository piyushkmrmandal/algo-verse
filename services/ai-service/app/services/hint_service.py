import time
import uuid

from sqlalchemy.ext.asyncio import AsyncSession

from app.config import Settings
from app.models.hint_request import HintRequest
from app.prompts.hint_prompt import HINT_SYSTEM_PROMPT, build_hint_user_message
from app.schemas.hints import HintResponse
from app.services.claude_client import complete


async def generate_hint(
    db: AsyncSession,
    settings: Settings,
    user_id: uuid.UUID,
    problem_id: uuid.UUID,
    submission_id: uuid.UUID | None,
    hint_level: int,
    current_code: str | None,
    language: str,
) -> HintResponse:
    user_msg = build_hint_user_message(
        str(problem_id), language, hint_level, current_code
    )

    hint_text, usage = await complete(
        system_prompt=HINT_SYSTEM_PROMPT,
        user_message=user_msg,
        max_tokens=800,
    )

    record = HintRequest(
        user_id=user_id,
        problem_id=problem_id,
        submission_id=submission_id,
        hint_level=hint_level,
        hint_text=hint_text,
        model_used=settings.claude_model,
        prompt_tokens=usage["prompt_tokens"],
        completion_tokens=usage["completion_tokens"],
        latency_ms=usage["latency_ms"],
    )
    db.add(record)
    await db.flush()

    return HintResponse(
        hint_text=hint_text,
        hint_level=hint_level,
        next_hint_available=hint_level < 5,
    )
