import uuid
from datetime import datetime

from sqlalchemy import CheckConstraint, DateTime, Numeric, String, Text, UniqueConstraint
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class CodeReview(Base):
    __tablename__ = "code_reviews"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(nullable=False)
    submission_id: Mapped[uuid.UUID] = mapped_column(nullable=False)
    review_text: Mapped[str] = mapped_column(Text, nullable=False)
    issues: Mapped[list | None] = mapped_column(JSONB, nullable=True)
    suggestions: Mapped[list | None] = mapped_column(JSONB, nullable=True)
    complexity_analysis: Mapped[dict | None] = mapped_column(JSONB, nullable=True)
    pattern_detected: Mapped[str | None] = mapped_column(String(100), nullable=True)
    quality_score: Mapped[float | None] = mapped_column(Numeric(4, 2), nullable=True)
    model_used: Mapped[str | None] = mapped_column(String(100), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )

    __table_args__ = (
        UniqueConstraint("submission_id", name="code_reviews_submission_unique"),
        CheckConstraint("quality_score BETWEEN 0 AND 10"),
    )
