from typing import Optional

try:
    import redis
except Exception:  # pragma: no cover
    redis = None

from app.core.config import settings


def get_redis() -> Optional["redis.Redis"]:
    """获取 Redis 客户端；未安装依赖时返回 None。"""
    if redis is None:
        return None
    return redis.Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        db=settings.redis_db,
        password=settings.redis_password,
        decode_responses=True,
    )
