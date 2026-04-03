"""Agent business services: QA, upload assist, message draft, and tag scoring."""

from typing import List, Dict, Any, Tuple
import json
import re


from app.schemas.task import AskResult, UploadAssistResult
from app.clients.llm_client import chat_completion
from app.clients.redis_client import get_redis
from app.core.config import settings


def extract_data(payload: Dict[str, Any]) -> Any:
    """Compatibility helper: unwrap backend's common response shape."""
    if isinstance(payload, dict) and "data" in payload:
        return payload.get("data")
    return payload


def _build_references(records: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    refs: List[Dict[str, Any]] = []
    for item in records[:5]:
        refs.append(
            {
                "videoId": item.get("id") or item.get("videoId"),
                "title": item.get("title"),
                "description": item.get("description"),
                "authorId": item.get("authorId"),
                "playCount": item.get("playCount"),
            }
        )
    return refs


async def build_rag_answer(question: str, records: List[Dict[str, Any]]) -> AskResult:
    refs = _build_references(records)
    if not refs:
        return AskResult(
            question=question,
            answer="未检索到相关视频，请尝试更具体的关键词。",
            references=[],
        )

    context_lines = []
    for idx, ref in enumerate(refs, start=1):
        context_lines.append(
            f"[{idx}] id={ref.get('videoId')} 标题={ref.get('title')} "
            f"描述={ref.get('description') or ''} 播放={ref.get('playCount') or 0}"
        )
    context_text = "\n".join(context_lines)

    try:
        answer = await chat_completion(
            messages=[
                {
                    "role": "system",
                    "content": "你是视频平台问答助手，只能基于给定检索结果回答，不要编造事实。",
                },
                {
                    "role": "user",
                    "content": (
                        f"用户问题：{question}\n\n"
                        f"检索结果：\n{context_text}\n\n"
                        "请输出：\n"
                        "1) 1~2句直接回答\n"
                        "2) 推荐先看的结果编号（如[1][3]）及理由"
                    ),
                },
            ],
            temperature=0.3,
        )
    except Exception:
        top_titles = [str(r.get("title") or "未知标题") for r in refs[:3]]
        answer = f"站内最相关的视频有：{'、'.join(top_titles)}。建议优先查看第一条。"

    return AskResult(question=question, answer=answer, references=refs)


def _keywords(text: str) -> List[str]:
    raw = re.findall(r"[\u4e00-\u9fffA-Za-z0-9_]+", (text or "").lower())
    return [word for word in raw if len(word) >= 2]


def suggest_tags(title: str, description: str, candidates: List[str]) -> List[str]:
    """Rule fallback: direct contain match + lightweight dictionary."""
    text = f"{title} {description}".lower()
    matched = [tag for tag in candidates if tag.lower() in text]
    if matched:
        return matched[:5]

    hardcoded_map = {
        "java": ["Java", "后端", "SpringBoot"],
        "spring": ["SpringBoot", "后端"],
        "vue": ["Vue", "前端"],
        "react": ["React", "前端"],
        "mysql": ["MySQL", "数据库"],
        "redis": ["Redis", "缓存"],
        "docker": ["Docker", "运维"],
        "篮球": ["篮球", "体育"],
        "足球": ["足球", "体育"],
        "nba": ["NBA", "篮球", "体育"],
        "cba": ["CBA", "篮球", "体育"],
    }
    hit: List[str] = []
    for key, tags in hardcoded_map.items():
        if key in text:
            hit.extend(tags)

    unique: List[str] = []
    for tag in hit:
        if tag not in unique and (not candidates or tag in candidates):
            unique.append(tag)
    return unique[:5]


def _parse_json_object(text: str) -> Dict[str, Any] | None:
    if not text:
        return None
    content = text.strip()
    if content.startswith("```"):
        content = re.sub(r"^```[a-zA-Z]*\s*", "", content)
        content = re.sub(r"\s*```$", "", content)
    try:
        parsed = json.loads(content)
        return parsed if isinstance(parsed, dict) else None
    except Exception:
        return None


def _clamp_confidence(value: Any, default: float = 0.5) -> float:
    try:
        v = float(value)
    except Exception:
        v = default
    return round(max(0.0, min(v, 1.0)), 4)


def _rule_score_tags(
    title: str,
    description: str,
    candidates: List[str],
    max_tags: int = 8,
) -> List[Dict[str, Any]]:
    """
    Rule-based tag scoring fallback.
    - Exact contain: high confidence
    - Token overlap: medium confidence
    - Otherwise fallback to low confidence top tags
    """
    normalized = [str(tag).strip() for tag in (candidates or []) if str(tag).strip()]
    if not normalized:
        return []

    text = f"{title or ''} {description or ''}".lower()
    text_tokens = set(_keywords(text))

    scored: List[Dict[str, Any]] = []
    for tag in normalized:
        tag_lower = tag.lower()
        tag_tokens = set(_keywords(tag_lower))
        contains = tag_lower in text and len(tag_lower) > 1
        overlap = len(tag_tokens & text_tokens) if tag_tokens else 0

        if contains:
            confidence = 0.88
            reason = "title_or_description_contains_tag"
        elif overlap > 0:
            confidence = min(0.78, 0.45 + 0.12 * overlap)
            reason = "token_overlap"
        else:
            confidence = 0.18
            reason = "weak_prior"

        scored.append(
            {
                "tag": tag,
                "confidence": _clamp_confidence(confidence),
                "reason": reason,
            }
        )

    scored.sort(key=lambda x: x["confidence"], reverse=True)
    top = scored[: max(1, max_tags)]

    # If all tags are weak, return only keyword-backed fallbacks; otherwise prefer empty result over arbitrary defaults.
    if top and top[0]["confidence"] < 0.3:
        fallback = suggest_tags(title, description, normalized)[:max_tags]
        return [
            {"tag": tag, "confidence": 0.35, "reason": "fallback_keyword_rule"}
            for tag in fallback
        ]
    return [item for item in top if item["confidence"] >= 0.3]


async def llm_recommend_tags_scored(
    title: str,
    description: str,
    candidates: List[str],
    max_tags: int = 8,
) -> Tuple[List[Dict[str, Any]], str]:
    """
    LLM-driven tag scoring with confidence.
    Only candidate tags are allowed in output.
    """
    normalized_candidates = [str(tag).strip() for tag in (candidates or []) if str(tag).strip()]
    if not normalized_candidates:
        return [], "no_candidates"

    lower_to_original: Dict[str, str] = {}
    for tag in normalized_candidates:
        key = tag.lower()
        if key not in lower_to_original:
            lower_to_original[key] = tag

    prompt = (
        "你是视频平台标签打分助手。\n"
        "请根据标题和简介，在候选标签里挑选最相关标签，并为每个标签给0~1置信度。\n"
        "约束：\n"
        "1) 只能从候选标签中选择；\n"
        "2) 返回 1~{max_tags} 个；\n"
        "3) 只输出JSON，不要Markdown。\n"
        '格式：{{"items":[{{"tag":"标签","confidence":0.92,"reason":"简短理由"}}],"reason":"总体说明"}}\n\n'
        f"标题：{title or ''}\n"
        f"简介：{description or ''}\n"
        f"候选标签：{normalized_candidates}\n"
    ).format(max_tags=max_tags)

    try:
        raw = await chat_completion(
            messages=[
                {"role": "system", "content": "你必须输出严格JSON。"},
                {"role": "user", "content": prompt},
            ],
            temperature=0.2,
        )
        parsed = _parse_json_object(raw)
        if not parsed:
            raise ValueError("llm_non_json")

        items = parsed.get("items")
        if not isinstance(items, list):
            raise ValueError("llm_items_invalid")

        scored: List[Dict[str, Any]] = []
        for item in items:
            if not isinstance(item, dict):
                continue
            raw_tag = item.get("tag")
            if not isinstance(raw_tag, str):
                continue
            hit = lower_to_original.get(raw_tag.strip().lower())
            if not hit:
                continue

            confidence = _clamp_confidence(item.get("confidence"), default=0.6)
            reason = item.get("reason") if isinstance(item.get("reason"), str) else "llm_match"

            if not any(x["tag"] == hit for x in scored):
                scored.append({"tag": hit, "confidence": confidence, "reason": reason})
            if len(scored) >= max_tags:
                break

        if not scored:
            raise ValueError("llm_no_valid_candidate")

        scored.sort(key=lambda x: x["confidence"], reverse=True)
        reason = parsed.get("reason")
        reason_text = reason.strip() if isinstance(reason, str) and reason.strip() else "llm_scored"
        return scored, f"llm:{reason_text}"
    except Exception as e:
        fallback = _rule_score_tags(title, description, normalized_candidates, max_tags=max_tags)
        return fallback, f"fallback_rule:{type(e).__name__}"


async def llm_recommend_tags(
    title: str,
    description: str,
    candidates: List[str],
    max_tags: int = 8,
) -> Tuple[List[str], str]:
    """Backward-compatible helper: returns only tag names."""
    scored, rationale = await llm_recommend_tags_scored(
        title=title,
        description=description,
        candidates=candidates,
        max_tags=max_tags,
    )
    tags = [item["tag"] for item in scored]
    return tags, rationale


def suggest_category(title: str, description: str, candidate_categories: List[Dict[str, Any]]) -> Dict[str, Any] | None:
    if not candidate_categories:
        return None

    text = f"{title} {description}".lower()
    best = None
    best_score = -1
    for category in candidate_categories:
        name = str(category.get("name", "")).lower()
        score = 0
        for token in _keywords(name):
            if token in text:
                score += 1
        if score > best_score:
            best_score = score
            best = category
    return best if best_score > 0 else None


def build_summary(title: str, description: str, tags: List[str]) -> str:
    short_desc = (description or "").strip()
    if len(short_desc) > 90:
        short_desc = short_desc[:90] + "..."
    if short_desc:
        return short_desc
    if title and title.strip():
        return f"本视频主题为《{title.strip()}》。"
    if tags:
        return f"本视频内容与{'、'.join(tags[:3])}相关。"
    return ""


def build_generated_title(title: str, tags: List[str], category_name: str | None = None) -> str:
    if title and title.strip():
        return title.strip()
    tag_set = set(tags or [])
    if "NBA" in tag_set or "篮球" in tag_set:
        return "NBA篮球精彩片段"
    if "足球" in tag_set:
        return "足球赛事精彩片段"
    if "游戏" in tag_set or "电子竞技" in tag_set:
        return "游戏实况精彩片段"
    if category_name:
        return f"{category_name}内容精选"
    if tags:
        return f"{'、'.join(tags[:2])}精彩分享"
    return "精彩视频分享"


async def analyze_cover_content(cover_url: str, candidate_tags: List[str], candidate_categories: List[Dict[str, Any]]) -> Dict[str, Any]:
    if not cover_url:
        return {"tags": [], "category_id": None, "category_name": None, "generated_title": ""}
    tag_text = "、".join(candidate_tags)
    cat_text = "、".join([f"{c.get('id')}:{c.get('name')}" for c in candidate_categories])
    prompt = (
        "请根据图片判断视频大类内容，并严格从候选标签和候选分类中选择。若不确定，请少选，不要猜。\n"
        "返回JSON字段：suggested_tags, suggested_category_id, generated_title, rationale"
    )
    raw = await chat_completion(
        messages=[
            {"role": "system", "content": "你是视频内容识别助手，输出必须是严格 JSON。"},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": f"候选标签：{tag_text}\n候选分类：{cat_text}\n{prompt}"},
                    {"type": "image_url", "image_url": {"url": cover_url}},
                ],
            },
        ],
        temperature=0.2,
    )
    parsed = _parse_json_object(raw) or {}
    valid_tag_set = set(candidate_tags)
    parsed_tags = [t for t in (parsed.get("suggested_tags") or []) if isinstance(t, str) and t in valid_tag_set][:5]
    valid_cat = {int(c.get("id")): c for c in candidate_categories if c.get("id") is not None}
    cat_id = parsed.get("suggested_category_id")
    cat = valid_cat.get(int(cat_id)) if isinstance(cat_id, int) and int(cat_id) in valid_cat else None
    generated_title = parsed.get("generated_title") if isinstance(parsed.get("generated_title"), str) else ""
    return {
        "tags": parsed_tags,
        "category_id": cat.get("id") if cat else None,
        "category_name": cat.get("name") if cat else None,
        "generated_title": generated_title.strip(),
    }


def build_semantic_document(title: str,
                            description: str,
                            tags: List[str],
                            category_name: str | None,
                            cover_semantics: Dict[str, Any] | None = None) -> str:
    cover_semantics = cover_semantics or {}
    cover_tags = cover_semantics.get("tags") or []
    cover_title = cover_semantics.get("generated_title") or ""
    sections = [
        f"标题：{(title or '').strip()}",
        f"分类：{(category_name or '').strip()}",
        f"标签：{'、'.join([tag for tag in tags if tag])}",
        f"简介：{(description or '').strip()}",
    ]
    if cover_tags:
        sections.append(f"封面语义标签：{'、'.join(cover_tags)}")
    if cover_title:
        sections.append(f"封面语义标题：{cover_title}")
    return "\n".join([section for section in sections if section and not section.endswith('：')])


async def understand_search_query(query: str) -> Dict[str, Any]:
    text = (query or "").strip()
    if not text:
        return {"intent": "empty", "rewritten_query": ""}
    lower = text.lower()
    rewritten = text
    if "我想学" in text:
        rewritten = text.replace("我想学", "").strip() + " 教程 入门"
    elif text.startswith("想学"):
        rewritten = text.replace("想学", "").strip() + " 教程 入门"
    elif "怎么学" in text or "入门" in text:
        rewritten = text + " 教程"
    if "springboot" in lower and "SpringBoot" not in rewritten:
        rewritten = rewritten.replace("springboot", "SpringBoot")
    return {"intent": "semantic_search", "rewritten_query": rewritten.strip()}


async def llm_upload_assist(
    title: str,
    description: str,
    candidate_tags: List[str],
    candidate_categories: List[Dict[str, Any]],
    related_records: List[Dict[str, Any]],
    fallback_tags: List[str],
    fallback_category: Dict[str, Any] | None,
    fallback_summary: str,
) -> UploadAssistResult:
    related_titles = [str(record.get("title", "")) for record in related_records[:5]]
    tag_text = "、".join(candidate_tags)
    cat_text = "、".join([f"{c.get('id')}:{c.get('name')}" for c in candidate_categories])

    prompt = (
        f"视频标题：{title}\n"
        f"视频简介：{description}\n"
        f"候选标签：{tag_text}\n"
        f"候选分区：{cat_text}\n"
        f"相似视频：{' | '.join(related_titles) if related_titles else '无'}\n\n"
        "请返回JSON，字段为：\n"
        "suggested_tags: string[]（仅能从候选标签中选，最多5个）\n"
        "suggested_category_id: number|null（仅能从候选分区中选）\n"
        "generated_title: string（10~40字）\n"
        "generated_summary: string（20~120字）\n"
        "rationale: string"
    )

    raw = await chat_completion(
        messages=[
            {"role": "system", "content": "你是视频投稿智能助手，输出必须是严格 JSON，不要 Markdown。"},
            {"role": "user", "content": prompt},
        ],
        temperature=0.4,
    )

    try:
        parsed = json.loads(raw)
    except Exception:
        return UploadAssistResult(
            suggested_tags=fallback_tags,
            suggested_category_id=(fallback_category.get("id") if fallback_category else None),
            suggested_category_name=(fallback_category.get("name") if fallback_category else None),
            generated_title=(title.strip() if title and title.strip() else ""),
            generated_summary=fallback_summary,
            rationale="LLM 输出解析失败，已回退规则结果。",
        )

    valid_tag_set = set(candidate_tags)
    parsed_tags = [t for t in (parsed.get("suggested_tags") or []) if isinstance(t, str) and t in valid_tag_set][:5]
    if not parsed_tags:
        parsed_tags = fallback_tags

    valid_cat = {int(c.get("id")): c for c in candidate_categories if c.get("id") is not None}
    cat_id = parsed.get("suggested_category_id")
    cat = valid_cat.get(int(cat_id)) if isinstance(cat_id, int) and int(cat_id) in valid_cat else fallback_category

    summary = parsed.get("generated_summary") if isinstance(parsed.get("generated_summary"), str) else fallback_summary
    generated_title = parsed.get("generated_title") if isinstance(parsed.get("generated_title"), str) else title
    rationale = parsed.get("rationale") if isinstance(parsed.get("rationale"), str) else "基于候选项和相似视频生成建议。"

    return UploadAssistResult(
        suggested_tags=parsed_tags,
        suggested_category_id=(cat.get("id") if cat else None),
        suggested_category_name=(cat.get("name") if cat else None),
        generated_title=(generated_title.strip() if generated_title else ""),
        generated_summary=summary,
        rationale=rationale,
    )


def draft_message_fallback(scenario: str, tone: str, latest_user_message: str) -> str:
    prefix = {
        "friendly": "你好呀，",
        "professional": "您好，",
        "brief": "你好，",
    }.get(tone, "你好，")

    body_map = {
        "reply": "感谢你的私信和反馈，我已经看到你的问题，会尽快给你明确回复。",
        "apology": "非常抱歉给你带来不好的体验，我这边会立即核查并处理。",
        "support": "你提到的问题我已经记录，先按步骤排查，我也会持续跟进。",
        "business": "合作意向已收到，辛苦补充预算、排期和目标人群，便于快速评估。",
    }
    body = body_map.get(scenario, body_map["reply"])

    quote = latest_user_message.strip()
    if len(quote) > 60:
        quote = quote[:60] + "..."

    return f"{prefix}{body}（你提到：{quote}）"


def _summary_cache_key(user_id: int | None, target_id: int) -> str:
    uid = user_id if user_id is not None else 0
    return f"agent:msg:summary:{uid}:{target_id}"


def get_cached_summary(user_id: int | None, target_id: int) -> str | None:
    redis_client = get_redis()
    if redis_client is None:
        return None
    try:
        return redis_client.get(_summary_cache_key(user_id, target_id))
    except Exception:
        return None


def set_cached_summary(user_id: int | None, target_id: int, summary: str) -> None:
    if not summary:
        return
    redis_client = get_redis()
    if redis_client is None:
        return
    try:
        redis_client.setex(_summary_cache_key(user_id, target_id), settings.redis_summary_ttl_seconds, summary)
    except Exception:
        return


async def draft_message_with_agent(
    scenario: str,
    tone: str,
    latest_user_message: str,
    conversation_brief: str,
    agent_role: str | None,
    custom_prompt: str | None,
) -> str:
    role_text = agent_role or "你是视频平台创作者私信助手，擅长礼貌、清晰、可执行地回复用户。"
    system_prompt = (
        f"{role_text}\n"
        "要求：\n"
        "1) 回复自然、具体，不要模板腔；\n"
        "2) 长度控制在 40~120 字；\n"
        "3) 不要自称 AI，不要编造事实；\n"
        "4) 结尾给出下一步动作或确认问题。"
    )
    if custom_prompt:
        system_prompt += f"\n补充要求：{custom_prompt}\n"

    user_prompt = (
        f"场景: {scenario}\n"
        f"语气: {tone}\n"
        f"会话摘要: {conversation_brief}\n"
        f"用户最新消息: {latest_user_message}\n\n"
        "请输出一段可直接发送的中文私信回复。"
    )

    content = await chat_completion(
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        temperature=0.6,
    )
    return content.strip()

