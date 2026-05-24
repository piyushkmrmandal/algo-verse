from fastapi import APIRouter, HTTPException, status

from app.dependencies import CurrentUserId
from app.schemas.explain import ExplainRequest, ExplainResponse
from app.services.explain_service import explain_concept

router = APIRouter(prefix="/explain", tags=["Explain"])


@router.post("", response_model=ExplainResponse, status_code=status.HTTP_200_OK)
async def explain(
    body: ExplainRequest,
    _user_id: CurrentUserId,
) -> ExplainResponse:
    try:
        return await explain_concept(
            concept=body.concept,
            user_level=body.user_level.value,
            context=body.context,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(exc),
        ) from exc
