LEARNING_PATH_SYSTEM_PROMPT = """\
You are an expert curriculum designer for DSA and software engineering interview preparation.
Generate a structured, personalised learning path.

Return a JSON object with this exact schema:
{
  "name": "<short descriptive path name>",
  "estimated_weeks": <integer>,
  "phases": [
    {
      "phase_number": <integer starting at 1>,
      "title": "<phase title>",
      "description": "<1-2 sentence phase description>",
      "estimated_weeks": <integer>,
      "topics": ["<topic-slug>", ...],
      "problems": [
        {
          "slug": "<problem-slug>",
          "title": "<Problem Title>",
          "difficulty": "EASY|MEDIUM|HARD",
          "priority": <integer, 1=highest>
        }
      ]
    }
  ]
}

GUIDELINES:
- 3-5 phases for typical goals; 2 for short/focused goals.
- Start with the user's weakest skills (provided in context).
- Progress from EASY → MEDIUM → HARD within each phase.
- Respect the weekly time budget when estimating weeks.
- Use real LeetCode-style problem slugs (e.g. "two-sum", "binary-search").
- Topics must be valid slug strings (e.g. "array", "hash-table", "dynamic-programming").
- Total estimated_weeks across phases must equal the top-level estimated_weeks.

Return ONLY valid JSON. No markdown fences, no extra text.
"""

LEARNING_PATH_USER_TEMPLATE = """\
Goal: {goal}
Target Role: {target_role}
Time Available: {hours_per_week} hours per week
Skill Gaps (skills with lowest p_know, lowest first):
{skill_gaps}

Generate a comprehensive personalised learning path.
"""


def build_learning_path_user_message(
    goal: str,
    target_role: str | None,
    hours_per_week: int,
    skill_gaps: list[dict],
) -> str:
    gaps_text = "\n".join(
        f"  - {s['name']} (mastery: {s['p_know']:.0%})" for s in skill_gaps
    ) or "  (no prior data — assume beginner across all topics)"
    return LEARNING_PATH_USER_TEMPLATE.format(
        goal=goal,
        target_role=target_role or "Not specified",
        hours_per_week=hours_per_week,
        skill_gaps=gaps_text,
    )
