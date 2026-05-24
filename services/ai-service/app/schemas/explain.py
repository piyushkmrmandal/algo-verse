from enum import Enum

from pydantic import BaseModel, Field


class UserLevel(str, Enum):
    BEGINNER = "BEGINNER"
    INTERMEDIATE = "INTERMEDIATE"
    ADVANCED = "ADVANCED"


class ExplainRequest(BaseModel):
    concept: str
    context: str | None = Field(None, max_length=1000)
    user_level: UserLevel


class CodeExample(BaseModel):
    title: str
    code: str
    explanation: str


class ExplainResponse(BaseModel):
    explanation: str
    examples: list[CodeExample]
    related_concepts: list[str]
