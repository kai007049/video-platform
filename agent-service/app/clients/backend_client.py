from typing import Dict, Any, Optional
import logging
import httpx

from app.core.config import settings

logger = logging.getLogger(__name__)


def _auth_headers(token: Optional[str]) -> Dict[str, str]:
    """构造调用 backend 所需的授权头。"""
    if not token:
        return {}
    return {"Authorization": token if token.startswith("Bearer ") else f"Bearer {token}"}


async def search_videos(keyword: str, page: int, size: int, token: Optional[str] = None) -> Dict[str, Any]:
    """调用 backend 搜索接口。"""
    logger.info(
        "[BackendClient] call /search keyword=%s page=%s size=%s hasToken=%s",
        keyword,
        page,
        size,
        bool(token),
    )
    async with httpx.AsyncClient(timeout=settings.request_timeout_seconds) as client:
        resp = await client.get(
            f"{settings.backend_base_url}/search",
            params={"keyword": keyword, "page": page, "size": size},
            headers=_auth_headers(token),
        )
        resp.raise_for_status()
        logger.info("[BackendClient] /search success status=%s", resp.status_code)
        return resp.json()


async def list_messages(target_id: int, page: int, size: int, token: Optional[str] = None) -> Dict[str, Any]:
    """调用 backend 私信列表接口。"""
    logger.info(
        "[BackendClient] call /message targetId=%s page=%s size=%s hasToken=%s",
        target_id,
        page,
        size,
        bool(token),
    )
    async with httpx.AsyncClient(timeout=settings.request_timeout_seconds) as client:
        resp = await client.get(
            f"{settings.backend_base_url}/message",
            params={"targetId": target_id, "page": page, "size": size},
            headers=_auth_headers(token),
        )
        resp.raise_for_status()
        logger.info("[BackendClient] /message success status=%s", resp.status_code)
        return resp.json()
