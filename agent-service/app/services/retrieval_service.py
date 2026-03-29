from typing import List, Dict, Any

try:
    from app.clients.embedding_client import embed_text
    from app.clients.milvus_client import search_similar_videos
except ImportError:
    from clients.embedding_client import embed_text
    from clients.milvus_client import search_similar_videos


def _normalize_ref(item: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "videoId": item.get("id") or item.get("videoId"),
        "title": item.get("title"),
        "description": item.get("description"),
        "authorId": item.get("authorId"),
        "playCount": item.get("playCount") or 0,
    }


async def hybrid_merge(question: str, keyword_records: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    Hybrid 合并：
    - keyword_records: 来自 Java /search
    - vector records: 来自 Milvus video_id 命中

    当前策略：
    1) keyword 结果基础分按 rank 递减
    2) vector 命中加分
    3) 按分数排序返回
    """
    score_map: Dict[int, float] = {}
    data_map: Dict[int, Dict[str, Any]] = {}

    for idx, item in enumerate(keyword_records[:20]):
        ref = _normalize_ref(item)
        vid = ref.get("videoId")
        if vid is None:
            continue
        vid = int(vid)
        data_map[vid] = ref
        score_map[vid] = score_map.get(vid, 0.0) + (1.0 / (idx + 1))

    try:
        vec = await embed_text(question)
        vector_hits = search_similar_videos(vec)
        for idx, h in enumerate(vector_hits):
            vid = int(h.get("videoId"))
            score_map[vid] = score_map.get(vid, 0.0) + (0.8 / (idx + 1))
            if vid not in data_map:
                # 纯向量命中但当前没有详情，先保留最小信息
                data_map[vid] = {
                    "videoId": vid,
                    "title": f"视频#{vid}",
                    "description": "",
                    "authorId": None,
                    "playCount": 0,
                }
    except Exception:
        # Milvus/embedding 异常时退化为 keyword-only
        pass

    ranked = sorted(score_map.items(), key=lambda x: x[1], reverse=True)
    out: List[Dict[str, Any]] = []
    for vid, _ in ranked[:10]:
        out.append(data_map[vid])
    return out
