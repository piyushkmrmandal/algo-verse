"""
Anthropic Claude client with prompt caching.

Prompt caching strategy:
  - System prompts marked with cache_control type=ephemeral (5-min TTL in Claude API).
  - Large static context blocks (problem descriptions) also cached when present.
  - This reduces input token costs by up to 90% on repeated calls with the same system prompt.
"""

import json
import time
from collections.abc import AsyncGenerator
from typing import Any

import anthropic

from app.config import get_settings

settings = get_settings()

_client: anthropic.AsyncAnthropic | None = None


def get_claude_client() -> anthropic.AsyncAnthropic:
    global _client
    if _client is None:
        _client = anthropic.AsyncAnthropic(api_key=settings.anthropic_api_key)
    return _client


async def complete(
    system_prompt: str,
    user_message: str,
    max_tokens: int | None = None,
    temperature: float = 0.3,
) -> tuple[str, dict[str, int]]:
    """Non-streaming completion. Returns (text, usage_dict)."""
    client = get_claude_client()
    start = time.monotonic()

    response = await client.messages.create(
        model=settings.claude_model,
        max_tokens=max_tokens or settings.claude_max_tokens,
        temperature=temperature,
        system=[
            {
                "type": "text",
                "text": system_prompt,
                "cache_control": {"type": "ephemeral"},
            }
        ],
        messages=[{"role": "user", "content": user_message}],
    )

    text = response.content[0].text if response.content else ""
    usage = {
        "prompt_tokens": response.usage.input_tokens,
        "completion_tokens": response.usage.output_tokens,
        "latency_ms": int((time.monotonic() - start) * 1000),
    }
    return text, usage


async def complete_json(
    system_prompt: str,
    user_message: str,
    max_tokens: int | None = None,
) -> tuple[Any, dict[str, int]]:
    """Non-streaming JSON completion. Parses and returns the JSON object."""
    text, usage = await complete(system_prompt, user_message, max_tokens, temperature=0.1)
    # Strip accidental markdown fences Claude might include despite instructions
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.split("\n", 1)[1].rsplit("```", 1)[0]
    return json.loads(cleaned), usage


async def stream_message(
    system_prompt: str,
    messages: list[dict[str, str]],
    context_block: str | None = None,
    max_tokens: int | None = None,
) -> AsyncGenerator[str, None]:
    """
    SSE-compatible streaming generator.

    Yields raw text deltas from Claude. The caller is responsible for
    formatting them into SSE events.

    If context_block is provided (e.g. problem description), it is injected
    as a cached user-turn prefix to avoid paying for it on every request.
    """
    client = get_claude_client()

    system_block: list[dict] = [
        {
            "type": "text",
            "text": system_prompt,
            "cache_control": {"type": "ephemeral"},
        }
    ]

    # Build the messages list, optionally prepending a cached context block
    api_messages: list[dict] = []
    if context_block:
        api_messages.append(
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": context_block,
                        "cache_control": {"type": "ephemeral"},
                    }
                ],
            }
        )
        api_messages.append({"role": "assistant", "content": "Understood. How can I help?"})

    api_messages.extend(messages)

    async with client.messages.stream(
        model=settings.claude_model,
        max_tokens=max_tokens or settings.claude_max_tokens,
        system=system_block,
        messages=api_messages,
    ) as stream:
        async for text in stream.text_stream:
            yield text
