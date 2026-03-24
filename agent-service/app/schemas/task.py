from pydantic import BaseModel, Field
from typing import List, Optional, Literal, Dict, Any


class TaskResponse(BaseModel):
    task_id: str
    status: str


class TaskResult(BaseModel):
    task_id: str
    status: str
    result: Optional[Dict[str, Any]] = None
    error: Optional[str] = None


class AskRequest(BaseModel):
    question: str = Field(..., description="User natural language query")
    page: int = Field(default=1, ge=1)
    size: int = Field(default=8, ge=1, le=20)


class AskResult(BaseModel):
    question: str
    answer: str
    references: List[Dict[str, Any]]


class UploadAssistRequest(BaseModel):
    title: str
    description: Optional[str] = None
    candidate_tags: List[str] = Field(default_factory=list)
    candidate_categories: List[Dict[str, Any]] = Field(default_factory=list)


class UploadAssistResult(BaseModel):
    suggested_tags: List[str]
    suggested_category_id: Optional[int] = None
    suggested_category_name: Optional[str] = None
    generated_summary: str
    rationale: str


class MessageDraftRequest(BaseModel):
    target_id: int
    scenario: Literal["reply", "apology", "support", "business"] = "reply"
    latest_user_message: str
    tone: Literal["friendly", "professional", "brief"] = "friendly"
    agent_role: Optional[str] = None
    custom_prompt: Optional[str] = None


class MessageDraftResult(BaseModel):
    draft: str
    conversation_brief: str
    suggested_next_actions: List[str]
