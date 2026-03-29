from typing import List, Dict, Any

try:
    from pymilvus import MilvusClient
except Exception:  # pragma: no cover
    MilvusClient = None

try:
    from app.core.config import settings
except ImportError:
    from core.config import settings


def _get_client() -> MilvusClient:
    if MilvusClient is None:
        raise RuntimeError("pymilvus 未安装，请先安装依赖")
    uri = f"http://{settings.milvus_host}:{settings.milvus_port}"
    return MilvusClient(uri=uri)


def search_similar_videos(vector: List[float], top_k: int | None = None) -> List[Dict[str, Any]]:
    """
    在 Milvus 中按向量检索相似视频。

    约定 collection 字段：
    - id(主键)
    - video_id(业务视频ID)
    - vector(向量)
    """
    client = _get_client()
    limit = top_k or settings.milvus_top_k
    result = client.search(
        collection_name=settings.milvus_collection,
        data=[vector],
        limit=limit,
        output_fields=["video_id"],
    )

    # pymilvus 返回二维列表（每个 query 一个命中列表）
    hits = result[0] if result else []
    out: List[Dict[str, Any]] = []
    for h in hits:
        entity = h.get("entity", {}) if isinstance(h, dict) else {}
        video_id = entity.get("video_id")
        score = h.get("distance") if isinstance(h, dict) else None
        if video_id is not None:
            out.append({"videoId": int(video_id), "score": float(score) if score is not None else 0.0})
    return out
