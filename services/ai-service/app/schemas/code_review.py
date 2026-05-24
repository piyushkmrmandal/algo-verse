import uuid
from datetime import datetime
from enum import Enum

from pydantic import BaseModel, Field


class IssueSeverity(str, Enum):
    ERROR = "ERROR"
    WARNING = "WARNING"
    INFO = "INFO"


class IssueType(str, Enum):
    BUG = "BUG"
    STYLE = "STYLE"
    PERFORMANCE = "PERFORMANCE"
    ROBUSTNESS = "ROBUSTNESS"
    SECURITY = "SECURITY"


class ReviewIssue(BaseModel):
    type: IssueType
    line: int | None = None
    message: str
    severity: IssueSeverity


class ComplexityAnalysis(BaseModel):
    time_complexity: str
    space_complexity: str
    explanation: str


class CodeReviewRequest(BaseModel):
    submission_id: uuid.UUID | None = None
    code: str = Field(..., max_length=65536)
    language: str
    problem_id: uuid.UUID


class CodeReviewResponse(BaseModel):
    id: uuid.UUID
    submission_id: uuid.UUID | None
    quality_score: int = Field(..., ge=0, le=100)
    complexity_analysis: ComplexityAnalysis
    pattern_detected: str | None
    issues: list[ReviewIssue]
    suggestions: list[ReviewIssue]
    created_at: datetime
