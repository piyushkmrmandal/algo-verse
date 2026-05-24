import uuid
from datetime import datetime
from enum import Enum

from pydantic import BaseModel


class SkillLevel(str, Enum):
    BEGINNER = "BEGINNER"
    INTERMEDIATE = "INTERMEDIATE"
    ADVANCED = "ADVANCED"


class SkillTrend(str, Enum):
    IMPROVING = "IMPROVING"
    STABLE = "STABLE"
    DECLINING = "DECLINING"


class SkillEntry(BaseModel):
    skill_id: str
    name: str
    p_know: float
    level: SkillLevel
    trend: SkillTrend


class SkillModelResponse(BaseModel):
    user_id: uuid.UUID
    updated_at: datetime
    skills: list[SkillEntry]


class BktUpdateRequest(BaseModel):
    skill_id: str
    correct: bool
