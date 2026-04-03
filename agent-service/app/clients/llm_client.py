"""LLM 客户端：负责调用 OpenAI 兼容的 chat completions 接口。"""

from typing import List, Dict, Any
import logging
import httpx

from app.core.config import settings

logger = logging.getLogger(__name__)


async def chat_completion(messages: List[Dict[str, Any]], temperature: float = 0.4) -> str:
    """调用 LLM 并返回首个候选文本。"""
    if not settings.llm_api_key:
        raise RuntimeError("未配置 LLM API Key（LLM_API_KEY / OPENAI_API_KEY）。")

    base = settings.llm_base_url.rstrip("/")
    url = f"{base}/v1/chat/completions"

    payload: Dict[str, Any] = {
        "model": settings.llm_model,
        "messages": messages,
        "temperature": temperature,
    }

    headers = {
        "Authorization": f"Bearer {settings.llm_api_key}",
        "Content-Type": "application/json",
    }

    async with httpx.AsyncClient(timeout=settings.llm_timeout_seconds) as client:
        logger.info(
            "[LLM] request model=%s temperature=%s messages=%s",
            settings.llm_model,
            temperature,
            len(messages),
        )
        resp = await client.post(url, json=payload, headers=headers)
        resp.raise_for_status()
        data = resp.json()
        logger.info("[LLM] response status=%s", resp.status_code)

    choices = data.get("choices", [])
    if not choices:
        raise RuntimeError("LLM 返回为空 choices。")

    content = choices[0].get("message", {}).get("content", "")
    if isinstance(content, list):
        text_parts = [part.get("text", "") for part in content if isinstance(part, dict)]
        content = "\n".join([part for part in text_parts if part])
    if not content:
        raise RuntimeError("LLM 未返回文本内容。")

    return str(content).strip()
