"""Embedding 客户端：负责文本向量化。"""

from typing import List, Dict, Any
import httpx

from app.core.config import settings


async def embed_text(text: str) -> List[float]:
    """调用 embedding 接口，将文本转换为向量。"""
    if not settings.embedding_api_key:
        raise RuntimeError("未配置 Embedding API Key（EMBEDDING_API_KEY/DASHSCOPE_API_KEY）")

    base = settings.embedding_base_url.rstrip("/")
    url = f"{base}/v1/embeddings"
    payload: Dict[str, Any] = {
        "model": settings.embedding_model,
        "input": text,
    }
    headers = {
        "Authorization": f"Bearer {settings.embedding_api_key}",
        "Content-Type": "application/json",
    }

    async with httpx.AsyncClient(timeout=settings.embedding_timeout_seconds) as client:
        resp = await client.post(url, json=payload, headers=headers)
        resp.raise_for_status()
        data = resp.json()

    arr = data.get("data") or []
    if not arr:
        raise RuntimeError("embedding 返回为空")
    vec = arr[0].get("embedding")
    if not isinstance(vec, list) or not vec:
        raise RuntimeError("embedding 返回格式错误")
    return [float(x) for x in vec]

