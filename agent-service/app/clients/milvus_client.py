"""Milvus 客户端：负责向量相似检索。"""

from typing import List, Dict, Any
import logging
import time

try:
    from pymilvus import MilvusClient
except Exception:  # pragma: no cover
    MilvusClient = None

from app.core.config import settings

logger = logging.getLogger(__name__)


class MilvusUnavailableError(RuntimeError):
    """Milvus 依赖或服务不可用。"""


def _get_client() -> MilvusClient:
    """创建 Milvus 客户端实例。"""
    if MilvusClient is None:
        raise MilvusUnavailableError("未安装 pymilvus，请先安装依赖")
    uri = f"http://{settings.milvus_host}:{settings.milvus_port}"
    return MilvusClient(uri=uri)


def ensure_collection(dimension: int) -> None:
    client = _get_client()
    collections = client.list_collections()
    if settings.milvus_collection in collections:
        return
    schema = client.create_schema(auto_id=False, enable_dynamic_field=True)
    schema.add_field(field_name="id", datatype="VARCHAR", is_primary=True, max_length=64)
    schema.add_field(field_name="video_id", datatype="INT64")
    schema.add_field(field_name="vector", datatype="FLOAT_VECTOR", dim=dimension)
    client.create_collection(collection_name=settings.milvus_collection, schema=schema)


def upsert_video_vector(video_id: int, vector: List[float], metadata: Dict[str, Any] | None = None) -> None:
    client = _get_client()
    ensure_collection(len(vector))
    row: Dict[str, Any] = {
        "id": str(video_id),
        "video_id": int(video_id),
        "vector": vector,
        "updated_at": int(time.time()),
    }
    if metadata:
        row.update(metadata)
    client.upsert(collection_name=settings.milvus_collection, data=[row])


def delete_video_vector(video_id: int) -> None:
    client = _get_client()
    client.delete(collection_name=settings.milvus_collection, ids=[str(video_id)])


def search_similar_videos(vector: List[float], top_k: int | None = None) -> List[Dict[str, Any]]:
    """根据向量检索相似视频，返回 videoId 与相似度分数。"""
    try:
        client = _get_client()
        limit = top_k or settings.milvus_top_k
        result = client.search(
            collection_name=settings.milvus_collection,
            data=[vector],
            limit=limit,
            output_fields=["video_id"],
        )
    except Exception:
        logger.warning("[Milvus] search unavailable", exc_info=True)
        return []

    hits = result[0] if result else []
    out: List[Dict[str, Any]] = []
    for hit in hits:
        entity = hit.get("entity", {}) if isinstance(hit, dict) else {}
        video_id = entity.get("video_id")
        score = hit.get("distance") if isinstance(hit, dict) else None
        if video_id is not None:
            out.append(
                {
                    "videoId": int(video_id),
                    "score": float(score) if score is not None else 0.0,
                }
            )
    return out

