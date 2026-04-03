from fastapi import APIRouter, Header, HTTPException
import logging
import json

from app.schemas.task import (
    TaskResponse,
    TaskResult,
    AskRequest,
    UploadAssistRequest,
    UploadAssistResult,
    MessageDraftRequest,
    MessageDraftResult,
    TagRecommendRequest,
    TagRecommendResult,
    SemanticSearchRequest,
    VideoVectorIndexRequest,
    VideoVectorDeleteRequest,
    RecommendVideosRequest,
    ContentAnalysisRequest,
    ContentAnalysisResult,
    ModerationRequest,
    ModerationResult,
)
from app.repos.task_store import create_task, complete_task, fail_task, get_task
from app.clients.backend_client import search_videos, list_messages
from app.services.retrieval_service import hybrid_merge
from app.clients.milvus_client import upsert_video_vector, delete_video_vector
from app.services.agent_service import (
    extract_data,
    build_rag_answer,
    suggest_tags,
    llm_recommend_tags_scored,
    suggest_category,
    build_summary,
    llm_upload_assist,
    draft_message_fallback,
    draft_message_with_agent,
    get_cached_summary,
    set_cached_summary,
    analyze_cover_content,
    build_generated_title,
    build_semantic_document,
    understand_search_query,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/tasks", tags=["tasks"])
agent_router = APIRouter(prefix="/agent", tags=["agent"])


@router.post("/ask", response_model=TaskResponse)
async def create_ask_task(req: AskRequest, authorization: str | None = Header(default=None)):
    task_id = create_task()
    logger.info("[Task][ask] start taskId=%s question=%s", task_id, req.question)
    try:
        search_resp = await search_videos(req.question, req.page, req.size, token=authorization)
        data = extract_data(search_resp) or {}
        records = data.get("records", []) if isinstance(data, dict) else []
        merged_records = await hybrid_merge(req.question, records)

        result = (await build_rag_answer(req.question, merged_records)).model_dump()
        complete_task(task_id, result)
        logger.info(
            "[Task][ask] success taskId=%s records=%s merged=%s",
            task_id,
            len(records),
            len(merged_records),
        )
        return TaskResponse(task_id=task_id, status="success")
    except Exception as e:
        fail_task(task_id, str(e))
        logger.exception("[Task][ask] failed taskId=%s", task_id)
        raise HTTPException(status_code=500, detail=f"ask task failed: {e}")


@router.post("/upload-assist", response_model=TaskResponse)
async def create_upload_assist_task(req: UploadAssistRequest, authorization: str | None = Header(default=None)):
    task_id = create_task()
    logger.info("[Task][upload-assist] start taskId=%s title=%s", task_id, req.title)
    try:
        related_resp = await search_videos(req.title, page=1, size=5, token=authorization)
        related_data = extract_data(related_resp) or {}
        related_records = related_data.get("records", []) if isinstance(related_data, dict) else []

        tags = suggest_tags(req.title, req.description or "", req.candidate_tags)
        if related_records:
            related_text = " ".join([str(r.get("title", "")) for r in related_records]).lower()
            for t in req.candidate_tags:
                if t.lower() in related_text and t not in tags:
                    tags.append(t)
            tags = tags[:5]

        cat = suggest_category(req.title, req.description or "", req.candidate_categories)
        summary = build_summary(req.title, req.description or "", tags)

        try:
            assist_result = await llm_upload_assist(
                title=req.title,
                description=req.description or "",
                candidate_tags=req.candidate_tags,
                candidate_categories=req.candidate_categories,
                related_records=related_records,
                fallback_tags=tags,
                fallback_category=cat,
                fallback_summary=summary,
            )
        except Exception:
            assist_result = UploadAssistResult(
                suggested_tags=tags,
                suggested_category_id=(cat.get("id") if cat else None),
                suggested_category_name=(cat.get("name") if cat else None),
                generated_summary=summary,
                rationale=f"基于标题/简介关键词匹配，并参考后端检索到的 {len(related_records)} 条相似视频。",
            )

        complete_task(task_id, assist_result.model_dump())
        logger.info(
            "[Task][upload-assist] success taskId=%s suggestedTags=%s",
            task_id,
            len(assist_result.suggested_tags),
        )
        return TaskResponse(task_id=task_id, status="success")
    except Exception as e:
        fail_task(task_id, str(e))
        logger.exception("[Task][upload-assist] failed taskId=%s", task_id)
        raise HTTPException(status_code=500, detail=f"upload assist task failed: {e}")


@router.post("/message-draft", response_model=TaskResponse)
async def create_message_draft_task(req: MessageDraftRequest, authorization: str | None = Header(default=None)):
    task_id = create_task()
    logger.info("[Task][message-draft] start taskId=%s targetId=%s", task_id, req.target_id)
    try:
        user_id = None
        cache_brief = get_cached_summary(user_id=user_id, target_id=req.target_id)
        if cache_brief:
            conversation_brief = cache_brief
        else:
            history_resp = await list_messages(req.target_id, page=1, size=10, token=authorization)
            data = extract_data(history_resp) or {}
            records = data.get("records", []) if isinstance(data, dict) else []

            conversation_brief = "最近会话较短。"
            if records:
                preview = []
                for m in records[-3:]:
                    content = str(m.get("content", "")).strip()
                    if content:
                        preview.append(content[:20])
                if preview:
                    conversation_brief = " / ".join(preview)
            set_cached_summary(user_id=user_id, target_id=req.target_id, summary=conversation_brief)

        try:
            draft = await draft_message_with_agent(
                scenario=req.scenario,
                tone=req.tone,
                latest_user_message=req.latest_user_message,
                conversation_brief=conversation_brief,
                agent_role=req.agent_role,
                custom_prompt=req.custom_prompt,
            )
        except Exception:
            draft = draft_message_fallback(req.scenario, req.tone, req.latest_user_message)

        payload = MessageDraftResult(
            draft=draft,
            conversation_brief=conversation_brief,
            suggested_next_actions=[
                "确认用户核心诉求",
                "给出可执行的下一步",
                "必要时引导到人工处理",
            ],
        ).model_dump()

        complete_task(task_id, payload)
        logger.info("[Task][message-draft] success taskId=%s", task_id)
        return TaskResponse(task_id=task_id, status="success")
    except Exception as e:
        fail_task(task_id, str(e))
        logger.exception("[Task][message-draft] failed taskId=%s", task_id)
        raise HTTPException(status_code=500, detail=f"message draft task failed: {e}")


@router.get("/{task_id}", response_model=TaskResult)
def get_task_result(task_id: str):
    task = get_task(task_id)
    if not task:
        return TaskResult(task_id=task_id, status="not_found", result=None)

    return TaskResult(
        task_id=task_id,
        status=task.get("status", "unknown"),
        result=task.get("result"),
        error=task.get("error"),
    )


async def _recommend_tags(req: TagRecommendRequest) -> TagRecommendResult:
    scored_tags, rationale = await llm_recommend_tags_scored(
        title=req.title or "",
        description=req.description or "",
        candidates=req.candidate_tags or [],
        max_tags=8,
    )
    tags = [item.get("tag") for item in scored_tags if isinstance(item, dict) and item.get("tag")]
    if not tags:
        tags = suggest_tags(req.title or "", req.description or "", req.candidate_tags or [])[:8]
        scored_tags = [{"tag": tag, "confidence": 0.35, "reason": "fallback_keyword_rule"} for tag in tags]
        rationale = f"{rationale};fallback_empty"
    return TagRecommendResult(tags=tags, rationale=rationale, scored_tags=scored_tags)


@router.post("/tag", response_model=TaskResponse)
async def create_tag_task(req: TagRecommendRequest):
    task_id = create_task()
    logger.info("[Task][tag] start taskId=%s title=%s", task_id, req.title)
    try:
        payload = (await _recommend_tags(req)).model_dump()
        complete_task(task_id, payload)
        logger.info(
            "[Task][tag] success taskId=%s tags=%s rationale=%s",
            task_id,
            len(payload.get("tags", [])),
            payload.get("rationale"),
        )
        return TaskResponse(task_id=task_id, status="success")
    except Exception as e:
        fail_task(task_id, str(e))
        logger.exception("[Task][tag] failed taskId=%s", task_id)
        raise HTTPException(status_code=500, detail=f"tag task failed: {e}")


@agent_router.post("/tag/recommend", response_model=TagRecommendResult)
async def recommend_tag(req: TagRecommendRequest):
    result = await _recommend_tags(req)
    logger.info(
        "[Agent][tag/recommend] success title=%s tags=%s rationale=%s",
        req.title,
        len(result.tags),
        result.rationale,
    )
    return result


@agent_router.post("/tag/score", response_model=TagRecommendResult)
async def score_tag(req: TagRecommendRequest):
    """标签打分接口：返回标签 + 置信度。"""
    result = await _recommend_tags(req)
    logger.info("[Agent][tag/score] success title=%s tags=%s", req.title, len(result.tags))
    return result


@agent_router.post("/search/semantic")
async def semantic_search(req: SemanticSearchRequest):
    from app.services.retrieval_service import vector_search

    try:
        understood = await understand_search_query(req.query)
        rewritten_query = understood.get("rewritten_query") or req.query
        results = await vector_search(rewritten_query, req.top_k)
        video_ids = [int(r.get("id")) for r in results if r.get("id")]
        scores = [float(r.get("score", 0.0)) for r in results]

        logger.info("[Agent][search/semantic] success query=%s rewritten=%s results=%s", req.query, rewritten_query, len(video_ids))
        return {"video_ids": video_ids, "scores": scores, "query": rewritten_query}
    except Exception:
        logger.error("[Agent][search/semantic] failed query=%s", req.query, exc_info=True)
        return {"video_ids": [], "scores": []}


@agent_router.post("/vector/index/video")
async def index_video_vector(req: VideoVectorIndexRequest):
    try:
        cover_semantics = {"tags": [], "generated_title": ""}
        if req.cover_url:
            try:
                cover_semantics = await analyze_cover_content(req.cover_url, req.tags or [], [])
            except Exception:
                logger.warning("[Agent][vector/index] cover analysis failed videoId=%s", req.video_id, exc_info=True)
        category_name = None
        semantic_text = build_semantic_document(
            req.title or "",
            req.description or "",
            req.tags or [],
            category_name,
            cover_semantics,
        )
        from app.clients.embedding_client import embed_text
        vector = await embed_text(semantic_text)
        upsert_video_vector(
            req.video_id,
            vector,
            {
                "title": req.title or "",
                "description": req.description or "",
                "tags": req.tags or [],
                "category_id": req.category_id,
                "semantic_text": semantic_text,
            },
        )
        logger.info("[Agent][vector/index] success videoId=%s", req.video_id)
        return {"success": True, "video_id": req.video_id}
    except Exception:
        logger.error("[Agent][vector/index] failed videoId=%s", req.video_id, exc_info=True)
        raise HTTPException(status_code=500, detail="vector index failed")


@agent_router.post("/vector/delete/video")
async def delete_video_vector_api(req: VideoVectorDeleteRequest):
    try:
        delete_video_vector(req.video_id)
        logger.info("[Agent][vector/delete] success videoId=%s", req.video_id)
        return {"success": True, "video_id": req.video_id}
    except Exception:
        logger.error("[Agent][vector/delete] failed videoId=%s", req.video_id, exc_info=True)
        raise HTTPException(status_code=500, detail="vector delete failed")


@agent_router.post("/recommend/videos")
async def recommend_videos(req: RecommendVideosRequest):
    from app.services.retrieval_service import vector_search

    try:
        query = req.context if req.context else "推荐视频"
        top_k = max(1, min(req.top_k, 100))
        results = await vector_search(query, top_k * 2)

        exclude_set = set(req.exclude_ids or [])
        video_ids = [int(r.get("id")) for r in results if r.get("id") and int(r.get("id")) not in exclude_set][:top_k]

        logger.info("[Agent][recommend/videos] success userId=%s results=%s", req.user_id, len(video_ids))
        return {"video_ids": video_ids}
    except Exception:
        logger.error("[Agent][recommend/videos] failed userId=%s", req.user_id, exc_info=True)
        return {"video_ids": []}


@agent_router.post("/content/analyze", response_model=ContentAnalysisResult)
async def analyze_content(req: ContentAnalysisRequest):
    try:
        scored_tags, _ = await llm_recommend_tags_scored(
            req.title, req.description or "", req.candidate_tags, max_tags=5
        )
        text_tags = [item.get("tag") for item in scored_tags if isinstance(item, dict) and item.get("tag")]
        visual_result = {"tags": [], "category_id": None, "category_name": None, "generated_title": ""}
        if req.cover_url:
            try:
                visual_result = await analyze_cover_content(req.cover_url, req.candidate_tags, req.candidate_categories)
            except Exception:
                logger.warning("[Agent][content/analyze] cover analysis failed coverUrl=%s", req.cover_url, exc_info=True)

        merged_tags = []
        for tag in [*(visual_result.get("tags") or []), *text_tags]:
            if tag and tag not in merged_tags:
                merged_tags.append(tag)
        merged_tags = merged_tags[:5]

        if merged_tags and scored_tags:
            boosted = []
            for item in scored_tags:
                if item.get("tag") in merged_tags:
                    boosted.append({
                        **item,
                        "confidence": min(0.98, float(item.get("confidence") or 0.6) + 0.12),
                    })
            scored_tags = boosted or scored_tags
        elif merged_tags:
            scored_tags = [{"tag": tag, "confidence": 0.72, "reason": "cover_signal"} for tag in merged_tags]

        category = None
        if visual_result.get("category_id") is not None:
            category = {
                "id": visual_result.get("category_id"),
                "name": visual_result.get("category_name"),
            }
        if category is None:
            category = suggest_category(req.title, req.description or "", req.candidate_categories)

        summary = build_summary(req.title, req.description or "", merged_tags)
        generated_title = build_generated_title(req.title, merged_tags, category.get("name") if category else None)
        if visual_result.get("generated_title"):
            generated_title = visual_result.get("generated_title")

        result = ContentAnalysisResult(
            suggested_tags=merged_tags,
            tag_scores=scored_tags,
            suggested_category_id=category.get("id") if category else None,
            suggested_category_name=category.get("name") if category else None,
            generated_title=generated_title,
            summary=summary,
        )

        logger.info("[Agent][content/analyze] success title=%s tags=%s cover=%s", req.title, len(merged_tags), bool(req.cover_url))
        return result
    except Exception:
        logger.error("[Agent][content/analyze] failed title=%s", req.title, exc_info=True)
        return ContentAnalysisResult(
            suggested_tags=[],
            tag_scores=[],
            suggested_category_id=None,
            suggested_category_name=None,
            generated_title="",
            summary="",
        )


@agent_router.post("/content/moderate", response_model=ModerationResult)
async def moderate_content(req: ModerationRequest):
    from app.clients.llm_client import chat_completion

    try:
        response = await chat_completion(
            messages=[
                {
                    "role": "system",
                    "content": "你是内容审核助手，判断文本是否包含违规内容。只输出JSON："
                               '{"is_risky":true/false,"risk_level":"safe/low/medium/high","reason":"原因"}'
                },
                {"role": "user", "content": f"审核内容：{req.content}"},
            ],
            temperature=0.1,
        )

        result_data = json.loads(response.strip())
        result = ModerationResult(
            is_risky=result_data.get("is_risky", False),
            risk_level=result_data.get("risk_level", "safe"),
            reason=result_data.get("reason", ""),
        )

        logger.info("[Agent][content/moderate] success isRisky=%s", result.is_risky)
        return result
    except Exception:
        logger.error("[Agent][content/moderate] failed", exc_info=True)
        return ModerationResult(is_risky=False, risk_level="safe", reason="审核服务异常")
