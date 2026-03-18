from fastapi import FastAPI
from pydantic import BaseModel, Field
from typing import List, Dict, Optional
from uuid import uuid4
import time

app = FastAPI(title="Agent Service")

# In-memory task store (replace with DB/Redis later)
TASKS: Dict[str, Dict] = {}


class TagRequest(BaseModel):
    title: str = Field(..., description="Video title")
    description: Optional[str] = Field(None, description="Video description")
    candidate_tags: List[str] = Field(default_factory=list, description="Allowed tags")


class TaskResponse(BaseModel):
    task_id: str
    status: str


class TaskResult(BaseModel):
    task_id: str
    status: str
    tags: List[str]
    rationale: Optional[str] = None


def _simple_tag_pipeline(title: str, description: Optional[str], candidates: List[str]) -> List[str]:
    """
    Minimal tag generation pipeline:
    1) keyword matching
    2) filter by candidate tag list
    """
    text = f"{title} {description or ''}".lower()
    matched = []
    for tag in candidates:
        if tag.lower() in text:
            matched.append(tag)
    # fallback: pick top 3 candidates if nothing matched
    if not matched:
        matched = candidates[:3]
    return matched


@app.post("/tasks/tag", response_model=TaskResponse)
def create_tag_task(req: TagRequest):
    task_id = str(uuid4())
    TASKS[task_id] = {
        "status": "running",
        "created_at": time.time(),
        "result": None,
    }
    tags = _simple_tag_pipeline(req.title, req.description, req.candidate_tags)
    TASKS[task_id]["status"] = "success"
    TASKS[task_id]["result"] = {
        "tags": tags,
        "rationale": "rule-based keyword match",
    }
    return TaskResponse(task_id=task_id, status="success")


@app.get("/tasks/{task_id}", response_model=TaskResult)
def get_task(task_id: str):
    task = TASKS.get(task_id)
    if not task:
        return TaskResult(task_id=task_id, status="not_found", tags=[])
    result = task.get("result") or {}
    return TaskResult(
        task_id=task_id,
        status=task.get("status", "unknown"),
        tags=result.get("tags", []),
        rationale=result.get("rationale"),
    )
