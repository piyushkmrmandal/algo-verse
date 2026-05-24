"""
ZPD-based problem recommendation engine.

Recommendation logic:
1. Fetch the user's BKT skill model.
2. Identify skills in the Zone of Proximal Development (0.4 ≤ p_know ≤ 0.7).
   If no ZPD skills, fall back to the weakest skills (p_know < 0.4).
3. Rank recommendations by confidence = p_know (closer to 0.5 = best fit).
4. Return problems that match those skills with appropriate difficulty.
"""

import uuid

from sqlalchemy.ext.asyncio import AsyncSession

from app.schemas.recommendations import DifficultyFit, Recommendation, RecommendationsResponse
from app.services.bkt_service import get_user_skills

# Canonical skill → display name map (mirrors topics.slug from problem-service)
SKILL_DISPLAY_NAMES: dict[str, str] = {
    "array": "Array",
    "hash-table": "Hash Table",
    "dynamic-programming": "Dynamic Programming",
    "binary-search": "Binary Search",
    "tree": "Tree",
    "graph": "Graph",
    "two-pointers": "Two Pointers",
    "sliding-window": "Sliding Window",
    "backtracking": "Backtracking",
    "greedy": "Greedy",
    "stack": "Stack",
    "queue": "Queue",
    "linked-list": "Linked List",
    "recursion": "Recursion",
    "sorting": "Sorting",
    "heap": "Heap / Priority Queue",
    "trie": "Trie",
    "union-find": "Union Find",
}

# Curated seed problems per skill for cold-start recommendations
SEED_PROBLEMS: dict[str, list[dict]] = {
    "array": [
        {"slug": "two-sum", "title": "Two Sum", "difficulty": "EASY"},
        {"slug": "best-time-to-buy-and-sell-stock", "title": "Best Time to Buy and Sell Stock", "difficulty": "EASY"},
        {"slug": "container-with-most-water", "title": "Container With Most Water", "difficulty": "MEDIUM"},
        {"slug": "3sum", "title": "3Sum", "difficulty": "MEDIUM"},
    ],
    "hash-table": [
        {"slug": "contains-duplicate", "title": "Contains Duplicate", "difficulty": "EASY"},
        {"slug": "valid-anagram", "title": "Valid Anagram", "difficulty": "EASY"},
        {"slug": "group-anagrams", "title": "Group Anagrams", "difficulty": "MEDIUM"},
        {"slug": "top-k-frequent-elements", "title": "Top K Frequent Elements", "difficulty": "MEDIUM"},
    ],
    "dynamic-programming": [
        {"slug": "climbing-stairs", "title": "Climbing Stairs", "difficulty": "EASY"},
        {"slug": "house-robber", "title": "House Robber", "difficulty": "MEDIUM"},
        {"slug": "coin-change", "title": "Coin Change", "difficulty": "MEDIUM"},
        {"slug": "longest-increasing-subsequence", "title": "Longest Increasing Subsequence", "difficulty": "MEDIUM"},
    ],
    "binary-search": [
        {"slug": "binary-search", "title": "Binary Search", "difficulty": "EASY"},
        {"slug": "find-minimum-in-rotated-sorted-array", "title": "Find Minimum in Rotated Sorted Array", "difficulty": "MEDIUM"},
        {"slug": "search-in-rotated-sorted-array", "title": "Search in Rotated Sorted Array", "difficulty": "MEDIUM"},
    ],
    "tree": [
        {"slug": "invert-binary-tree", "title": "Invert Binary Tree", "difficulty": "EASY"},
        {"slug": "maximum-depth-of-binary-tree", "title": "Maximum Depth of Binary Tree", "difficulty": "EASY"},
        {"slug": "validate-binary-search-tree", "title": "Validate Binary Search Tree", "difficulty": "MEDIUM"},
        {"slug": "binary-tree-level-order-traversal", "title": "Binary Tree Level Order Traversal", "difficulty": "MEDIUM"},
    ],
    "graph": [
        {"slug": "number-of-islands", "title": "Number of Islands", "difficulty": "MEDIUM"},
        {"slug": "clone-graph", "title": "Clone Graph", "difficulty": "MEDIUM"},
        {"slug": "course-schedule", "title": "Course Schedule", "difficulty": "MEDIUM"},
        {"slug": "word-ladder", "title": "Word Ladder", "difficulty": "HARD"},
    ],
    "two-pointers": [
        {"slug": "valid-palindrome", "title": "Valid Palindrome", "difficulty": "EASY"},
        {"slug": "container-with-most-water", "title": "Container With Most Water", "difficulty": "MEDIUM"},
        {"slug": "trapping-rain-water", "title": "Trapping Rain Water", "difficulty": "HARD"},
    ],
    "sliding-window": [
        {"slug": "best-time-to-buy-and-sell-stock", "title": "Best Time to Buy and Sell Stock", "difficulty": "EASY"},
        {"slug": "longest-substring-without-repeating-characters", "title": "Longest Substring Without Repeating Characters", "difficulty": "MEDIUM"},
        {"slug": "minimum-window-substring", "title": "Minimum Window Substring", "difficulty": "HARD"},
    ],
}


def _difficulty_for_level(p_know: float) -> DifficultyFit:
    if p_know < 0.35:
        return DifficultyFit.EASY
    if p_know < 0.65:
        return DifficultyFit.MEDIUM
    return DifficultyFit.HARD


def _confidence(p_know: float) -> float:
    # Highest confidence at p_know = 0.5 (peak ZPD)
    return 1.0 - abs(p_know - 0.5) * 2


async def get_recommendations(
    db: AsyncSession,
    user_id: uuid.UUID,
    count: int,
    rec_type: str,
) -> list[Recommendation]:
    skills = await get_user_skills(db, user_id)

    if not skills:
        return _cold_start_recommendations(count, rec_type)

    # Sort by ZPD proximity (closest to 0.5 first)
    skills_sorted = sorted(skills, key=lambda s: abs(float(s.p_know) - 0.5))

    recommendations: list[Recommendation] = []
    seen_slugs: set[str] = set()

    for skill in skills_sorted:
        if len(recommendations) >= count:
            break

        p_know = float(skill.p_know)
        target_difficulty = _difficulty_for_level(p_know)
        skill_name = SKILL_DISPLAY_NAMES.get(skill.skill_id, skill.skill_id)
        seed = SEED_PROBLEMS.get(skill.skill_id, [])

        if rec_type == "TOPIC":
            recommendations.append(
                Recommendation(
                    type="TOPIC",
                    target_id=f"top_{skill.skill_id}",
                    target_slug=skill.skill_id,
                    target_title=skill_name,
                    reason=f"Your {skill_name} mastery is at {p_know:.0%}. Focused practice here will yield the highest XP gain.",
                    confidence_score=round(_confidence(p_know), 3),
                    estimated_difficulty_fit=None,
                )
            )
        else:
            for problem in seed:
                if len(recommendations) >= count:
                    break
                if problem["slug"] in seen_slugs:
                    continue
                if problem["difficulty"] != target_difficulty.value:
                    continue
                seen_slugs.add(problem["slug"])
                recommendations.append(
                    Recommendation(
                        type="PROBLEM",
                        target_id=f"prb_{problem['slug']}",
                        target_slug=problem["slug"],
                        target_title=problem["title"],
                        reason=f"Strengthen your {skill_name} skills — this problem is a great fit for your current mastery level ({p_know:.0%}).",
                        confidence_score=round(_confidence(p_know), 3),
                        estimated_difficulty_fit=target_difficulty,
                    )
                )

    return recommendations[:count]


def _cold_start_recommendations(count: int, rec_type: str) -> list[Recommendation]:
    starters = [
        ("two-sum", "Two Sum", "array", "Array", "EASY"),
        ("valid-anagram", "Valid Anagram", "hash-table", "Hash Table", "EASY"),
        ("climbing-stairs", "Climbing Stairs", "dynamic-programming", "Dynamic Programming", "EASY"),
        ("binary-search", "Binary Search", "binary-search", "Binary Search", "EASY"),
        ("invert-binary-tree", "Invert Binary Tree", "tree", "Tree", "EASY"),
    ]
    recs = []
    for slug, title, skill_id, skill_name, diff in starters[:count]:
        recs.append(
            Recommendation(
                type="PROBLEM" if rec_type != "TOPIC" else "TOPIC",
                target_id=f"prb_{slug}" if rec_type != "TOPIC" else f"top_{skill_id}",
                target_slug=slug if rec_type != "TOPIC" else skill_id,
                target_title=title if rec_type != "TOPIC" else skill_name,
                reason="Great starting point for building your DSA foundation.",
                confidence_score=0.75,
                estimated_difficulty_fit=DifficultyFit(diff) if rec_type != "TOPIC" else None,
            )
        )
    return recs


