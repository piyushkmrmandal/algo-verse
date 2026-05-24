import uuid

from fastapi import APIRouter, HTTPException, status

from app.dependencies import AppSettings, CurrentUserId, DbSession
from app.schemas.learning_path import CreateLearningPathRequest, LearningPathResponse
from app.services.learning_path_service import create_learning_path, get_learning_path

router = APIRouter(prefix="/learning-path", tags=["LearningPath"])


@router.post("", response_model=LearningPathResponse, status_code=status.HTTP_201_CREATED)
async def create(
    body: CreateLearningPathRequest,
    user_id: CurrentUserId,
    db: DbSession,
    settings: AppSettings,
) -> LearningPathResponse:
    try:
        return await create_learning_path(
            db=db,
            settings=settings,
            user_id=user_id,
            goal=body.goal,
            target_role=body.target_role,
            hours_per_week=body.time_available_hours_per_week,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(exc),
        ) from exc


@router.get("/{path_id}", response_model=LearningPathResponse)
async def get_path(
    path_id: uuid.UUID,
    user_id: CurrentUserId,
    db: DbSession,
) -> LearningPathResponse:
    result = await get_learning_path(db, user_id, path_id)
    if result is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Learning path not found")
    return result
