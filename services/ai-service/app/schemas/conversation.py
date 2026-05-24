import uuid
from datetime import datetime

from pydantic import BaseModel, Field


class ConversationRequest(BaseModel):
    problem_id: uuid.UUID | None = None
    message: str = Field(..., max_length=4000)
    thread_id: str | None = None
    current_code: str | None = None
    language: str | None = None
    hint_level: int = Field(0, ge=0, le=5)


class ConversationSummary(BaseModel):
    id: uuid.UUID
    thread_id: str | None
    problem_id: uuid.UUID | None
    problem_title: str | None
    message_count: int
    last_message_at: datetime
    created_at: datetime


class ConversationsResponse(BaseModel):
    data: list[ConversationSummary]
