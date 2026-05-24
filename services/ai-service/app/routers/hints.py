from fastapi import APIRouter, HTTPException, status

from app.dependencies import AppSettings, CurrentUserId, DbSession
from app.schemas.hints import HintRequest, HintResponse
from app.services.hint_service import generate_hint

router = APIRouter(prefix="/hints", tags=["Hints"])


@router.post("", response_model=HintResponse, status_code=status.HTTP_200_OK)
async def get_hint(
    body: HintRequest,
    user_id: CurrentUserId,
    db: DbSession,
    settings: AppSettings,
) -> HintResponse:
    try:
        return await generate_hint(
            db=db,
            settings=settings,
            user_id=user_id,
            problem_id=body.problem_id,
            submission_id=body.submission_id,
            hint_level=body.hint_level,
            current_code=body.current_code,
            language=body.language,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(exc),
        ) from exc
