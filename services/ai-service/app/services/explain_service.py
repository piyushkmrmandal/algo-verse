from app.prompts.explain_prompt import EXPLAIN_SYSTEM_PROMPT, build_explain_user_message
from app.schemas.explain import CodeExample, ExplainResponse
from app.services.claude_client import complete_json


async def explain_concept(
    concept: str,
    user_level: str,
    context: str | None,
) -> ExplainResponse:
    user_msg = build_explain_user_message(concept, user_level, context)
    raw, _ = await complete_json(
        system_prompt=EXPLAIN_SYSTEM_PROMPT,
        user_message=user_msg,
        max_tokens=2500,
    )

    examples = [CodeExample(**e) for e in raw.get("examples", [])]
    return ExplainResponse(
        explanation=raw.get("explanation", ""),
        examples=examples,
        related_concepts=raw.get("related_concepts", []),
    )
