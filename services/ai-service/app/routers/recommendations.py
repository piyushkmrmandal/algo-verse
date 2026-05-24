from fastapi import APIRouter, Query

from app.dependencies import CurrentUserId, DbSession
from app.schemas.recommendations import Recommendation, RecommendationsResponse
from app.services.recommendation_service import get_recommendations

router = APIRouter(prefix="/recommendations", tags=["Recommendations"])


@router.get("", response_model=RecommendationsResponse)
async def recommendations(
    user_id: CurrentUserId,
    db: DbSession,
    count: int = Query(default=5, ge=1, le=20),
    type: str = Query(default="PROBLEM", pattern="^(PROBLEM|TOPIC)$"),
) -> RecommendationsResponse:
    data = await get_recommendations(db, user_id, count, type)
    return RecommendationsResponse(data=data)
