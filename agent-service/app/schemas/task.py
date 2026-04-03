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
    generated_title: str = ""
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


class TagRecommendRequest(BaseModel):
    title: str = ""
    description: Optional[str] = None
    candidate_tags: List[str] = Field(default_factory=list)


class TagRecommendResult(BaseModel):
    tags: List[str]
    rationale: str = "llm + fallback rule based matching"
    # Each item: {"tag": "篮球", "confidence": 0.91, "reason": "..."}
    scored_tags: List[Dict[str, Any]] = Field(default_factory=list)


class SemanticSearchRequest(BaseModel):
    query: str = Field(..., description="搜索关键词")
    top_k: int = Field(default=20, ge=1, le=50, description="返回结果数量")


class VideoVectorIndexRequest(BaseModel):
    video_id: int
    video_url: Optional[str] = None
    cover_url: Optional[str] = None
    title: str = ""
    description: Optional[str] = None
    category_id: Optional[int] = None
    tags: List[str] = Field(default_factory=list)


class VideoVectorDeleteRequest(BaseModel):
    video_id: int


class RecommendVideosRequest(BaseModel):
    user_id: int
    context: str = ""
    exclude_ids: List[int] = Field(default_factory=list)
    top_k: int = Field(default=20, ge=1, le=100)


class ContentAnalysisRequest(BaseModel):
    title: str
    description: Optional[str] = None
    cover_url: Optional[str] = None
    candidate_tags: List[str] = Field(default_factory=list)
    candidate_categories: List[Dict[str, Any]] = Field(default_factory=list)


class ContentAnalysisResult(BaseModel):
    suggested_tags: List[str]
    # Same structure as TagRecommendResult.scored_tags
    tag_scores: List[Dict[str, Any]] = Field(default_factory=list)
    suggested_category_id: Optional[int] = None
    suggested_category_name: Optional[str] = None
    generated_title: str = ""
    summary: str


class ModerationRequest(BaseModel):
    content: str = Field(..., description="待审核内容")


class ModerationResult(BaseModel):
    is_risky: bool = Field(default=False, description="是否包含风险内容")
    risk_level: str = Field(default="safe", description="风险等级: safe/low/medium/high")
    reason: str = Field(default="", description="风险原因")
