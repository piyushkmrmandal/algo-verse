from fastapi import APIRouter, HTTPException, status

from app.dependencies import AppSettings, CurrentUserId, DbSession
from app.schemas.code_review import CodeReviewRequest, CodeReviewResponse
from app.services.review_service import review_code

router = APIRouter(prefix="/code-review", tags=["CodeReview"])


@router.post("", response_model=CodeReviewResponse, status_code=status.HTTP_200_OK)
async def review(
    body: CodeReviewRequest,
    user_id: CurrentUserId,
    db: DbSession,
    settings: AppSettings,
) -> CodeReviewResponse:
    try:
        return await review_code(
            db=db,
            settings=settings,
            user_id=user_id,
            submission_id=body.submission_id,
            problem_id=body.problem_id,
            code=body.code,
            language=body.language,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(exc),
        ) from exc
