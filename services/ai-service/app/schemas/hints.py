import uuid

from pydantic import BaseModel, Field


class HintRequest(BaseModel):
    problem_id: uuid.UUID
    submission_id: uuid.UUID | None = None
    hint_level: int = Field(..., ge=1, le=5)
    current_code: str | None = None
    language: str


class HintResponse(BaseModel):
    hint_text: str
    hint_level: int
    next_hint_available: bool
