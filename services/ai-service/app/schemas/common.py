import uuid
from datetime import datetime

from pydantic import BaseModel


class ErrorDetail(BaseModel):
    field: str
    issue: str


class ErrorResponse(BaseModel):
    trace_id: str
    code: str
    message: str
    timestamp: datetime
    details: list[ErrorDetail] | None = None
