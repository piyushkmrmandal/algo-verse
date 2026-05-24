import uuid
from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import Settings
from app.models.learning_path import LearningPath
from app.prompts.learning_path_prompt import (
    LEARNING_PATH_SYSTEM_PROMPT,
    build_learning_path_user_message,
)
from app.schemas.learning_path import (
    Difficulty,
    LearningPathResponse,
    LearningPhase,
    PhaseProblem,
)
from app.services.bkt_service import get_user_skills
from app.services.claude_client import complete_json


async def create_learning_path(
    db: AsyncSession,
    settings: Settings,
    user_id: uuid.UUID,
    goal: str,
    target_role: str | None,
    hours_per_week: int,
) -> LearningPathResponse:
    skills = await get_user_skills(db, user_id)
    skill_gaps = sorted(
        [{"name": s.skill_id, "p_know": float(s.p_know)} for s in skills],
        key=lambda x: x["p_know"],
    )

    user_msg = build_learning_path_user_message(goal, target_role, hours_per_week, skill_gaps)
    raw, _ = await complete_json(
        system_prompt=LEARNING_PATH_SYSTEM_PROMPT,
        user_message=user_msg,
        max_tokens=3000,
    )

    # Build the DB record — store phases in problem_sequence JSONB
    estimated_hours = (raw.get("estimated_weeks", 12) * hours_per_week)
    record = LearningPath(
        user_id=user_id,
        name=raw.get("name", goal[:200]),
        goal=goal[:100],
        topics=[t for ph in raw.get("phases", []) for t in ph.get("topics", [])],
        problem_sequence=raw.get("phases", []),
        estimated_hours=estimated_hours,
    )
    db.add(record)
    await db.flush()

    return _to_response(record, raw)


async def get_learning_path(
    db: AsyncSession,
    user_id: uuid.UUID,
    path_id: uuid.UUID,
) -> LearningPathResponse | None:
    result = await db.execute(
        select(LearningPath).where(
            LearningPath.id == path_id,
            LearningPath.user_id == user_id,
        )
    )
    record = result.scalar_one_or_none()
    if record is None:
        return None
    return _to_response(record, {"phases": record.problem_sequence or []})


def _to_response(record: LearningPath, raw: dict) -> LearningPathResponse:
    phases = []
    for ph in raw.get("phases", []):
        problems = [
            PhaseProblem(
                slug=p["slug"],
                title=p["title"],
                difficulty=Difficulty(p["difficulty"]),
                priority=p.get("priority", 1),
            )
            for p in ph.get("problems", [])
        ]
        phases.append(
            LearningPhase(
                phase_number=ph["phase_number"],
                title=ph["title"],
                description=ph.get("description"),
                estimated_weeks=ph["estimated_weeks"],
                topics=ph.get("topics", []),
                problems=problems,
            )
        )

    total_weeks = sum(ph.estimated_weeks for ph in phases) or raw.get("estimated_weeks", 12)

    return LearningPathResponse(
        id=record.id,
        user_id=record.user_id,
        goal=record.goal or "",
        target_role=None,
        estimated_weeks=total_weeks,
        phases=phases,
        created_at=record.created_at,
        updated_at=record.updated_at,
    )
