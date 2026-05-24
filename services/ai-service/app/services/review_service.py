import uuid
from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import Settings
from app.models.code_review import CodeReview
from app.prompts.review_prompt import REVIEW_SYSTEM_PROMPT, build_review_user_message
from app.schemas.code_review import (
    CodeReviewResponse,
    ComplexityAnalysis,
    ReviewIssue,
)
from app.services.claude_client import complete_json


async def review_code(
    db: AsyncSession,
    settings: Settings,
    user_id: uuid.UUID,
    submission_id: uuid.UUID | None,
    problem_id: uuid.UUID,
    code: str,
    language: str,
) -> CodeReviewResponse:
    # Return cached review for same submission if it exists
    if submission_id:
        result = await db.execute(
            select(CodeReview).where(CodeReview.submission_id == submission_id)
        )
        existing = result.scalar_one_or_none()
        if existing:
            return _to_response(existing)

    user_msg = build_review_user_message(str(problem_id), language, code)
    raw, _ = await complete_json(
        system_prompt=REVIEW_SYSTEM_PROMPT,
        user_message=user_msg,
        max_tokens=2000,
    )

    quality_score_raw = raw.get("quality_score", 70)
    # Normalise: DB stores 0–10, API spec says 0–100; we keep 0–100 in response
    quality_score_db = round(quality_score_raw / 10, 2)

    record = CodeReview(
        user_id=user_id,
        submission_id=submission_id or uuid.uuid4(),
        review_text=raw.get("review_text", ""),
        issues=raw.get("issues", []),
        suggestions=raw.get("suggestions", []),
        complexity_analysis=raw.get("complexity_analysis", {}),
        pattern_detected=raw.get("pattern_detected"),
        quality_score=quality_score_db,
        model_used=settings.claude_model,
    )
    db.add(record)
    await db.flush()

    return _to_response(record, quality_score_override=quality_score_raw)


def _to_response(record: CodeReview, quality_score_override: int | None = None) -> CodeReviewResponse:
    issues = [ReviewIssue(**i) for i in (record.issues or [])]
    suggestions = [ReviewIssue(**s) for s in (record.suggestions or [])]
    ca = record.complexity_analysis or {}
    complexity = ComplexityAnalysis(
        time_complexity=ca.get("time_complexity", "Unknown"),
        space_complexity=ca.get("space_complexity", "Unknown"),
        explanation=ca.get("explanation", ""),
    )
    score = quality_score_override if quality_score_override is not None else int(float(record.quality_score or 0) * 10)
    return CodeReviewResponse(
        id=record.id,
        submission_id=record.submission_id,
        quality_score=score,
        complexity_analysis=complexity,
        pattern_detected=record.pattern_detected,
        issues=issues,
        suggestions=suggestions,
        created_at=record.created_at,
    )
