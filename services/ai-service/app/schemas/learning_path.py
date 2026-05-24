import uuid
from datetime import datetime
from enum import Enum

from pydantic import BaseModel, Field


class Difficulty(str, Enum):
    EASY = "EASY"
    MEDIUM = "MEDIUM"
    HARD = "HARD"


class PhaseProblem(BaseModel):
    slug: str
    title: str
    difficulty: Difficulty
    priority: int


class LearningPhase(BaseModel):
    phase_number: int
    title: str
    description: str | None = None
    estimated_weeks: int
    topics: list[str]
    problems: list[PhaseProblem]


class CreateLearningPathRequest(BaseModel):
    goal: str = Field(..., max_length=500)
    target_role: str | None = Field(None, max_length=200)
    time_available_hours_per_week: int = Field(..., ge=1, le=80)


class LearningPathResponse(BaseModel):
    id: uuid.UUID
    user_id: uuid.UUID
    goal: str
    target_role: str | None
    estimated_weeks: int
    phases: list[LearningPhase]
    created_at: datetime
    updated_at: datetime
