from typing import Dict, Any, Optional
import httpx

try:
    from app.core.config import settings
except ImportError:
    from core.config import settings


def _auth_headers(token: Optional[str]) -> Dict[str, str]:
    if not token:
        return {}
    return {"Authorization": token if token.startswith("Bearer ") else f"Bearer {token}"}


async def search_videos(keyword: str, page: int, size: int, token: Optional[str] = None) -> Dict[str, Any]:
    async with httpx.AsyncClient(timeout=settings.request_timeout_seconds) as client:
        resp = await client.get(
            f"{settings.backend_base_url}/search",
            params={"keyword": keyword, "page": page, "size": size},
            headers=_auth_headers(token),
        )
        resp.raise_for_status()
        return resp.json()


async def list_messages(target_id: int, page: int, size: int, token: Optional[str] = None) -> Dict[str, Any]:
    async with httpx.AsyncClient(timeout=settings.request_timeout_seconds) as client:
        resp = await client.get(
            f"{settings.backend_base_url}/message",
            params={"targetId": target_id, "page": page, "size": size},
            headers=_auth_headers(token),
        )
        resp.raise_for_status()
        return resp.json()
