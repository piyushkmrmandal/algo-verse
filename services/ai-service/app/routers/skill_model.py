from datetime import datetime, timezone

from fastapi import APIRouter, HTTPException, status

from app.dependencies import CurrentUserId, DbSession
from app.schemas.skill_model import BktUpdateRequest, SkillEntry, SkillModelResponse
from app.services.bkt_service import (
    classify_level,
    classify_trend,
    get_user_skills,
    update_bkt,
)

router = APIRouter(prefix="/skill-model", tags=["SkillModel"])

_SKILL_NAMES = {
    "array": "Array",
    "hash-table": "Hash Table",
    "dynamic-programming": "Dynamic Programming",
    "binary-search": "Binary Search",
    "tree": "Tree",
    "graph": "Graph",
    "two-pointers": "Two Pointers",
    "sliding-window": "Sliding Window",
    "backtracking": "Backtracking",
    "greedy": "Greedy",
    "stack": "Stack",
    "linked-list": "Linked List",
    "heap": "Heap",
    "trie": "Trie",
    "union-find": "Union Find",
}


@router.get("", response_model=SkillModelResponse)
async def get_skill_model(
    user_id: CurrentUserId,
    db: DbSession,
) -> SkillModelResponse:
    rows = await get_user_skills(db, user_id)
    skills = [
        SkillEntry(
            skill_id=r.skill_id,
            name=_SKILL_NAMES.get(r.skill_id, r.skill_id.replace("-", " ").title()),
            p_know=round(float(r.p_know), 5),
            level=classify_level(float(r.p_know)),
            trend=classify_trend(float(r.p_know), r.attempt_count),
        )
        for r in rows
    ]
    last_updated = max((r.last_updated for r in rows), default=datetime.now(timezone.utc))
    return SkillModelResponse(user_id=user_id, updated_at=last_updated, skills=skills)


@router.post("/update", status_code=status.HTTP_200_OK)
async def update_skill(
    body: BktUpdateRequest,
    user_id: CurrentUserId,
    db: DbSession,
) -> dict:
    new_p_know = await update_bkt(db, user_id, body.skill_id, body.correct)
    return {"skill_id": body.skill_id, "p_know": round(new_p_know, 5)}
