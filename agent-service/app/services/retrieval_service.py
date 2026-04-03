"""检索融合服务：关键词召回 + 向量召回。"""

from typing import List, Dict, Any

from app.clients.embedding_client import embed_text
from app.clients.milvus_client import search_similar_videos


def _normalize_ref(item: Dict[str, Any]) -> Dict[str, Any]:
    """统一视频记录字段，避免上下游字段差异。"""
    return {
        "videoId": item.get("id") or item.get("videoId"),
        "title": item.get("title"),
        "description": item.get("description"),
        "authorId": item.get("authorId"),
        "playCount": item.get("playCount") or 0,
    }


async def hybrid_merge(question: str, keyword_records: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    混合检索融合策略：
    1) 关键词检索按位置给基础分
    2) 向量检索命中补充分
    3) 按总分排序返回 TopN
    """
    score_map: Dict[int, float] = {}
    data_map: Dict[int, Dict[str, Any]] = {}

    # 关键词召回：rank 越靠前分数越高
    for idx, item in enumerate(keyword_records[:20]):
        ref = _normalize_ref(item)
        vid = ref.get("videoId")
        if vid is None:
            continue
        vid = int(vid)
        data_map[vid] = ref
        score_map[vid] = score_map.get(vid, 0.0) + (1.0 / (idx + 1))

    # 向量召回失败时自动降级到关键词召回，不影响主流程
    try:
        vec = await embed_text(question)
        vector_hits = search_similar_videos(vec)
        for idx, hit in enumerate(vector_hits):
            vid = int(hit.get("videoId"))
            score_map[vid] = score_map.get(vid, 0.0) + (0.8 / (idx + 1))
            if vid not in data_map:
                data_map[vid] = {
                    "videoId": vid,
                    "title": f"视频#{vid}",
                    "description": "",
                    "authorId": None,
                    "playCount": 0,
                }
    except Exception:
        pass

    ranked = sorted(score_map.items(), key=lambda pair: pair[1], reverse=True)
    out: List[Dict[str, Any]] = []
    for vid, _ in ranked[:10]:
        out.append(data_map[vid])
    return out


async def vector_search(query: str, top_k: int = 20) -> List[Dict[str, Any]]:
    """
    纯向量检索：用于语义搜索
    返回格式：[{"id": videoId, "score": similarity}, ...]
    """
    try:
        vec = await embed_text(query)
        hits = search_similar_videos(vec, top_k)
        results = []
        for hit in hits:
            results.append({
                "id": int(hit.get("videoId")),
                "score": float(hit.get("score", 0.0))
            })
        return results
    except Exception:
        return []

