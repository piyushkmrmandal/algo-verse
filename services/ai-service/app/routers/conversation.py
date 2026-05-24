from datetime import datetime

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from app.dependencies import CurrentUserId, DbSession
from app.schemas.conversation import (
    ConversationRequest,
    ConversationSummary,
    ConversationsResponse,
)
from app.services.conversation_service import list_conversations, stream_tutor_response

router = APIRouter(tags=["Conversation"])


@router.post("/conversation")
async def conversation(
    body: ConversationRequest,
    user_id: CurrentUserId,
    db: DbSession,
) -> StreamingResponse:
    generator = stream_tutor_response(
        db=db,
        user_id=user_id,
        problem_id=body.problem_id,
        message=body.message,
        thread_id=body.thread_id,
        current_code=body.current_code,
        language=body.language,
        hint_level=body.hint_level,
    )
    return StreamingResponse(
        generator,
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )


@router.get("/conversations", response_model=ConversationsResponse)
async def get_conversations(
    user_id: CurrentUserId,
    db: DbSession,
) -> ConversationsResponse:
    rows = await list_conversations(db, user_id)
    summaries = [
        ConversationSummary(
            id=r.id,
            thread_id=r.thread_id,
            problem_id=r.problem_id,
            problem_title=None,
            message_count=len(r.messages or []),
            last_message_at=r.updated_at,
            created_at=r.created_at,
        )
        for r in rows
    ]
    return ConversationsResponse(data=summaries)
