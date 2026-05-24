import uuid
from datetime import datetime

from sqlalchemy import CheckConstraint, DateTime, Integer, Numeric, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class SkillModel(Base):
    __tablename__ = "skill_models"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(nullable=False, index=True)
    skill_id: Mapped[str] = mapped_column(String(100), nullable=False)
    p_know: Mapped[float] = mapped_column(Numeric(6, 5), nullable=False, default=0.10)
    p_learn: Mapped[float] = mapped_column(Numeric(6, 5), nullable=False, default=0.30)
    p_guess: Mapped[float] = mapped_column(Numeric(6, 5), nullable=False, default=0.20)
    p_slip: Mapped[float] = mapped_column(Numeric(6, 5), nullable=False, default=0.10)
    attempt_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    last_updated: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )

    __table_args__ = (
        UniqueConstraint("user_id", "skill_id", name="skill_models_unique"),
        CheckConstraint("p_know BETWEEN 0 AND 1"),
        CheckConstraint("p_learn BETWEEN 0 AND 1"),
        CheckConstraint("p_guess BETWEEN 0 AND 1"),
        CheckConstraint("p_slip BETWEEN 0 AND 1"),
        CheckConstraint("attempt_count >= 0"),
    )
