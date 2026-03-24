from typing import List, Dict, Any
import httpx

try:
    from app.core.config import settings
except ImportError:
    from core.config import settings


async def chat_completion(messages: List[Dict[str, str]], temperature: float = 0.4) -> str:
    if not settings.llm_api_key:
        raise RuntimeError("LLM_API_KEY/OPENAI_API_KEY 未配置")

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
        resp = await client.post(url, json=payload, headers=headers)
        resp.raise_for_status()
        data = resp.json()

    choices = data.get("choices", [])
    if not choices:
        raise RuntimeError("LLM 返回为空")

    content = choices[0].get("message", {}).get("content", "")
    if not content:
        raise RuntimeError("LLM 未返回文本内容")

    return content.strip()
