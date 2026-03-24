from typing import List, Dict, Any
import json

try:
    from app.schemas.task import AskResult, UploadAssistResult
    from app.clients.llm_client import chat_completion
except ImportError:
    from schemas.task import AskResult, UploadAssistResult
    from clients.llm_client import chat_completion


def extract_data(payload: Dict[str, Any]) -> Any:
    if isinstance(payload, dict) and "data" in payload:
        return payload.get("data")
    return payload


def _build_references(records: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    refs: List[Dict[str, Any]] = []
    for item in records[:5]:
        refs.append(
            {
                "videoId": item.get("id"),
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
        return AskResult(question=question, answer="未检索到相关视频，可以尝试换一个更具体的关键词。", references=[])

    context_lines = []
    for idx, r in enumerate(refs, start=1):
        context_lines.append(
            f"[{idx}] id={r.get('videoId')} 标题={r.get('title')} 描述={r.get('description') or ''} 播放={r.get('playCount') or 0}"
        )
    context_text = "\n".join(context_lines)

    try:
        answer = await chat_completion(
            messages=[
                {
                    "role": "system",
                    "content": "你是视频平台检索问答助手。仅基于给定检索结果回答，禁止编造不存在的视频。回答中文、简洁、可执行。",
                },
                {
                    "role": "user",
                    "content": (
                        f"用户问题：{question}\n\n"
                        f"检索结果：\n{context_text}\n\n"
                        "请给出：\n"
                        "1) 1-2句直接回答\n"
                        "2) 推荐先看哪些编号（如 [1][3]）及理由"
                    ),
                },
            ],
            temperature=0.3,
        )
    except Exception:
        top_titles = [f"《{r.get('title', '未知标题')}》" for r in refs[:3]]
        answer = f"基于站内检索，和你问题最相关的是：{'、'.join(top_titles)}。建议优先查看第一条。"

    return AskResult(question=question, answer=answer, references=refs)


def _keywords(text: str) -> List[str]:
    raw = text.lower().replace("，", " ").replace(",", " ").split()
    return [w for w in raw if len(w) >= 2]


def suggest_tags(title: str, description: str, candidates: List[str]) -> List[str]:
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
        "算法": ["算法"],
    }
    hit = []
    for k, tags in hardcoded_map.items():
        if k in text:
            hit.extend(tags)

    if not hit:
        return candidates[:3]

    uniq = []
    for t in hit:
        if t not in uniq and (not candidates or t in candidates):
            uniq.append(t)
    return uniq[:5] if uniq else candidates[:3]


def suggest_category(title: str, description: str, candidate_categories: List[Dict[str, Any]]) -> Dict[str, Any] | None:
    if not candidate_categories:
        return None

    text = f"{title} {description}".lower()
    best = None
    best_score = -1

    for c in candidate_categories:
        name = str(c.get("name", "")).lower()
        score = 0
        for token in _keywords(name):
            if token in text:
                score += 1
        if score > best_score:
            best_score = score
            best = c

    return best or candidate_categories[0]


def build_summary(title: str, description: str, tags: List[str]) -> str:
    tag_text = "、".join(tags[:3]) if tags else "相关主题"
    short_desc = (description or "").strip()
    if len(short_desc) > 90:
        short_desc = short_desc[:90] + "..."
    return f"本视频围绕{tag_text}展开，主题为《{title}》。{short_desc}"


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
    related_titles = [str(r.get("title", "")) for r in related_records[:5]]
    tag_text = "、".join(candidate_tags)
    cat_text = "、".join([f"{c.get('id')}:{c.get('name')}" for c in candidate_categories])

    prompt = (
        f"视频标题：{title}\n"
        f"视频简介：{description}\n"
        f"候选标签：{tag_text}\n"
        f"候选分类：{cat_text}\n"
        f"相似视频：{' | '.join(related_titles) if related_titles else '无'}\n\n"
        "请返回 JSON，字段为：\n"
        "suggested_tags: string[]（仅能从候选标签中选，最多5个）\n"
        "suggested_category_id: number|null（仅能从候选分类中选）\n"
        "generated_summary: string（40~120字）\n"
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
            generated_summary=fallback_summary,
            rationale="LLM 输出解析失败，已使用规则回退",
        )

    valid_tag_set = set(candidate_tags)
    parsed_tags = [t for t in (parsed.get("suggested_tags") or []) if isinstance(t, str) and t in valid_tag_set][:5]
    if not parsed_tags:
        parsed_tags = fallback_tags

    valid_cat = {int(c.get("id")): c for c in candidate_categories if c.get("id") is not None}
    cat_id = parsed.get("suggested_category_id")
    cat = valid_cat.get(int(cat_id)) if isinstance(cat_id, int) and int(cat_id) in valid_cat else fallback_category

    summary = parsed.get("generated_summary") if isinstance(parsed.get("generated_summary"), str) else fallback_summary
    rationale = parsed.get("rationale") if isinstance(parsed.get("rationale"), str) else "基于相似视频和候选项做建议"

    return UploadAssistResult(
        suggested_tags=parsed_tags,
        suggested_category_id=(cat.get("id") if cat else None),
        suggested_category_name=(cat.get("name") if cat else None),
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
        "support": "你提到的问题我已经记录，建议你先按步骤排查，我也会继续跟进。",
        "business": "合作意向已收到，辛苦补充预算、排期和目标人群，方便我们快速评估。",
    }
    body = body_map.get(scenario, body_map["reply"])

    quote = latest_user_message.strip()
    if len(quote) > 60:
        quote = quote[:60] + "..."

    return f"{prefix}{body}（你提到：{quote}）"


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
        "1) 回复必须自然、具体，不要模板腔。\n"
        "2) 长度控制在 40~120 字。\n"
        "3) 不要自称 AI，不要编造事实。\n"
        "4) 结尾给出下一步动作或确认问题。\n"
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
