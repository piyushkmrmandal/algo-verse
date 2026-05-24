"""
Bayesian Knowledge Tracing (BKT) engine — Python implementation.

BKT models the probability that a learner has mastered a skill using
four parameters per (user, skill) pair:
  p_know  — P(mastery): latent probability the student knows the skill right now
  p_learn — P(learn):   probability of transitioning unknown → known after an attempt
  p_guess — P(guess):   probability of correct response despite not knowing
  p_slip  — P(slip):    probability of incorrect response despite knowing

Update equations after each observation:
  P(Know | correct)   = [p_know × (1 - p_slip)]
                        / [p_know × (1 - p_slip) + (1 - p_know) × p_guess]
  P(Know | incorrect) = [p_know × p_slip]
                        / [p_know × p_slip + (1 - p_know) × (1 - p_guess)]
  P(Know_next)        = P(Know | obs) + (1 - P(Know | obs)) × p_learn
"""

import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.skill_model import SkillModel

# Default BKT priors for new (user, skill) pairs
_DEFAULT_P_KNOW = 0.10
_DEFAULT_P_LEARN = 0.30
_DEFAULT_P_GUESS = 0.20
_DEFAULT_P_SLIP = 0.10

# ZPD thresholds
_ZPD_LOW = 0.40
_ZPD_HIGH = 0.70


def _posterior(p_know: float, p_learn: float, p_guess: float, p_slip: float, correct: bool) -> float:
    if correct:
        numerator = p_know * (1 - p_slip)
        denominator = numerator + (1 - p_know) * p_guess
    else:
        numerator = p_know * p_slip
        denominator = numerator + (1 - p_know) * (1 - p_guess)

    p_know_given = numerator / denominator if denominator > 0 else p_know
    p_know_next = p_know_given + (1 - p_know_given) * p_learn
    return max(0.0, min(1.0, p_know_next))


async def update_bkt(
    db: AsyncSession,
    user_id: uuid.UUID,
    skill_id: str,
    correct: bool,
) -> float:
    """Apply one BKT observation. Returns the updated p_know."""
    result = await db.execute(
        select(SkillModel).where(
            SkillModel.user_id == user_id,
            SkillModel.skill_id == skill_id,
        )
    )
    row = result.scalar_one_or_none()

    if row is None:
        row = SkillModel(
            user_id=user_id,
            skill_id=skill_id,
            p_know=_DEFAULT_P_KNOW,
            p_learn=_DEFAULT_P_LEARN,
            p_guess=_DEFAULT_P_GUESS,
            p_slip=_DEFAULT_P_SLIP,
        )
        db.add(row)
        await db.flush()

    new_p_know = _posterior(
        float(row.p_know),
        float(row.p_learn),
        float(row.p_guess),
        float(row.p_slip),
        correct,
    )
    row.p_know = new_p_know
    row.attempt_count += 1
    return new_p_know


async def get_user_skills(
    db: AsyncSession,
    user_id: uuid.UUID,
) -> list[SkillModel]:
    result = await db.execute(
        select(SkillModel)
        .where(SkillModel.user_id == user_id)
        .order_by(SkillModel.p_know.desc())
    )
    return list(result.scalars().all())


async def get_zpd_skills(
    db: AsyncSession,
    user_id: uuid.UUID,
) -> list[SkillModel]:
    """Return skills in the Zone of Proximal Development (0.4 ≤ p_know ≤ 0.7)."""
    result = await db.execute(
        select(SkillModel)
        .where(
            SkillModel.user_id == user_id,
            SkillModel.p_know >= _ZPD_LOW,
            SkillModel.p_know <= _ZPD_HIGH,
        )
        .order_by(SkillModel.p_know)
    )
    return list(result.scalars().all())


def classify_level(p_know: float) -> str:
    if p_know < 0.40:
        return "BEGINNER"
    if p_know < 0.75:
        return "INTERMEDIATE"
    return "ADVANCED"


def classify_trend(p_know: float, attempt_count: int) -> str:
    # Without historical data we infer from current mastery and attempts
    if attempt_count == 0:
        return "STABLE"
    if p_know >= _ZPD_HIGH:
        return "STABLE"
    if attempt_count < 3:
        return "IMPROVING"
    return "IMPROVING" if p_know > _DEFAULT_P_KNOW else "STABLE"
