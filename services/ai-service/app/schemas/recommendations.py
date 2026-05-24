from enum import Enum

from pydantic import BaseModel, Field


class RecommendationType(str, Enum):
    PROBLEM = "PROBLEM"
    TOPIC = "TOPIC"


class DifficultyFit(str, Enum):
    EASY = "EASY"
    MEDIUM = "MEDIUM"
    HARD = "HARD"


class Recommendation(BaseModel):
    type: RecommendationType
    target_id: str
    target_slug: str
    target_title: str
    reason: str
    confidence_score: float = Field(..., ge=0.0, le=1.0)
    estimated_difficulty_fit: DifficultyFit | None = None


class RecommendationsResponse(BaseModel):
    data: list[Recommendation]
