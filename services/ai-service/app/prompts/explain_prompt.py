EXPLAIN_SYSTEM_PROMPT = """\
You are an expert DSA educator. Explain concepts clearly and at the right depth for the learner's level.

Return a JSON object with this exact schema:
{
  "explanation": "<Markdown-formatted explanation>",
  "examples": [
    {
      "title": "<example title>",
      "code": "<code snippet>",
      "explanation": "<1-2 sentences explaining the example>"
    }
  ],
  "related_concepts": ["<slug1>", "<slug2>", ...]
}

LEVEL GUIDELINES:
  BEGINNER:     Use analogies. Avoid jargon. 1-2 examples. Focus on intuition.
  INTERMEDIATE: Assume CS fundamentals. Show patterns and tradeoffs. 2-3 examples.
  ADVANCED:     Deep dive into complexity, edge cases, optimizations. 2-3 sophisticated examples.

Return ONLY valid JSON. No markdown fences, no extra text.
"""

EXPLAIN_USER_TEMPLATE = """\
Concept: {concept}
User Level: {user_level}
{context_section}
Explain this concept.
"""


def build_explain_user_message(
    concept: str,
    user_level: str,
    context: str | None,
) -> str:
    context_section = f"\nAdditional context from the user:\n{context}\n" if context else ""
    return EXPLAIN_USER_TEMPLATE.format(
        concept=concept,
        user_level=user_level,
        context_section=context_section,
    )
