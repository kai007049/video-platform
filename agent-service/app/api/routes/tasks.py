from fastapi import APIRouter, Header, HTTPException

try:
    from app.schemas.task import (
        TaskResponse,
        TaskResult,
        AskRequest,
        UploadAssistRequest,
        UploadAssistResult,
        MessageDraftRequest,
        MessageDraftResult,
    )
    from app.repos.task_store import create_task, complete_task, fail_task, get_task
    from app.clients.backend_client import search_videos, list_messages
    from app.services.retrieval_service import hybrid_merge
    from app.services.agent_service import (
        extract_data,
        build_rag_answer,
        suggest_tags,
        suggest_category,
        build_summary,
        llm_upload_assist,
        draft_message_fallback,
        draft_message_with_agent,
        get_cached_summary,
        set_cached_summary,
    )
except ImportError:
    from schemas.task import (
        TaskResponse,
        TaskResult,
        AskRequest,
        UploadAssistRequest,
        UploadAssistResult,
        MessageDraftRequest,
        MessageDraftResult,
    )
    from repos.task_store import create_task, complete_task, fail_task, get_task
    from clients.backend_client import search_videos, list_messages
    from services.retrieval_service import hybrid_merge
    from services.agent_service import (
        extract_data,
        build_rag_answer,
        suggest_tags,
        suggest_category,
        build_summary,
        llm_upload_assist,
        draft_message_fallback,
        draft_message_with_agent,
        get_cached_summary,
        set_cached_summary,
    )

router = APIRouter(prefix="/tasks", tags=["tasks"])


@router.post("/ask", response_model=TaskResponse)
async def create_ask_task(req: AskRequest, authorization: str | None = Header(default=None)):
    task_id = create_task()
    try:
        search_resp = await search_videos(req.question, req.page, req.size, token=authorization)
        data = extract_data(search_resp) or {}
        records = data.get("records", []) if isinstance(data, dict) else []
        merged_records = await hybrid_merge(req.question, records)

        result = (await build_rag_answer(req.question, merged_records)).model_dump()
        complete_task(task_id, result)
        return TaskResponse(task_id=task_id, status="success")
    except Exception as e:
        fail_task(task_id, str(e))
        raise HTTPException(status_code=500, detail=f"ask task failed: {e}")


@router.post("/upload-assist", response_model=TaskResponse)
async def create_upload_assist_task(req: UploadAssistRequest, authorization: str | None = Header(default=None)):
    task_id = create_task()
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
                rationale=f"基于标题/简介关键词匹配，并参考后端检索到的{len(related_records)}条相似视频",
            )

        complete_task(task_id, assist_result.model_dump())
        return TaskResponse(task_id=task_id, status="success")
    except Exception as e:
        fail_task(task_id, str(e))
        raise HTTPException(status_code=500, detail=f"upload assist task failed: {e}")


@router.post("/message-draft", response_model=TaskResponse)
async def create_message_draft_task(req: MessageDraftRequest, authorization: str | None = Header(default=None)):
    task_id = create_task()
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
        return TaskResponse(task_id=task_id, status="success")
    except Exception as e:
        fail_task(task_id, str(e))
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
