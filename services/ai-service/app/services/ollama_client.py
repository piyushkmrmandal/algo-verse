"""
Ollama client for local open-source LLM inference.

Supported models (configure via AI_MODEL env var):
  - qwen2.5-coder:7b   (default — strong at code, fast)
  - codellama:13b       (Meta's code-focused model)
  - deepseek-coder-v2   (strong reasoning + code)
  - llama3.1:8b         (general purpose conversations)

Run Ollama locally:  brew install ollama && ollama serve
Pull a model:        ollama pull qwen2.5-coder:7b
"""

import json
import time
from collections.abc import AsyncGenerator
from typing import Any

import httpx

from app.config import get_settings

settings = get_settings()

_client: httpx.AsyncClient | None = None


def get_client() -> httpx.AsyncClient:
    global _client
    if _client is None:
        _client = httpx.AsyncClient(
            base_url=settings.ollama_base_url,
            timeout=httpx.Timeout(120.0),
        )
    return _client


async def complete(
    system_prompt: str,
    user_message: str,
    max_tokens: int | None = None,
    temperature: float = 0.3,
) -> tuple[str, dict[str, int]]:
    """Non-streaming completion. Returns (text, usage_dict)."""
    client = get_client()
    start = time.monotonic()

    payload = {
        "model": settings.ai_model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_message},
        ],
        "stream": False,
        "options": {
            "num_predict": max_tokens or settings.ai_max_tokens,
            "temperature": temperature,
        },
    }

    response = await client.post("/api/chat", json=payload)
    response.raise_for_status()
    data = response.json()

    text = data.get("message", {}).get("content", "")
    usage = {
        "prompt_tokens": data.get("prompt_eval_count", 0),
        "completion_tokens": data.get("eval_count", 0),
        "latency_ms": int((time.monotonic() - start) * 1000),
    }
    return text, usage


async def complete_json(
    system_prompt: str,
    user_message: str,
    max_tokens: int | None = None,
) -> tuple[Any, dict[str, int]]:
    """Non-streaming JSON completion. Parses and returns the JSON object."""
    augmented = user_message + "\n\nRespond with valid JSON only. No markdown fences or explanation."
    text, usage = await complete(system_prompt, augmented, max_tokens, temperature=0.1)
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
    Yields raw text deltas. The caller formats them into SSE events.
    """
    client = get_client()

    api_messages: list[dict] = [{"role": "system", "content": system_prompt}]
    if context_block:
        api_messages.append({"role": "user", "content": context_block})
        api_messages.append({"role": "assistant", "content": "Understood. How can I help?"})
    api_messages.extend(messages)

    payload = {
        "model": settings.ai_model,
        "messages": api_messages,
        "stream": True,
        "options": {"num_predict": max_tokens or settings.ai_max_tokens},
    }

    async with client.stream("POST", "/api/chat", json=payload) as response:
        response.raise_for_status()
        async for line in response.aiter_lines():
            if not line:
                continue
            try:
                chunk = json.loads(line)
                content = chunk.get("message", {}).get("content", "")
                if content:
                    yield content
                if chunk.get("done"):
                    break
            except json.JSONDecodeError:
                continue
