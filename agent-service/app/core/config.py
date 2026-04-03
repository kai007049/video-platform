"""Agent Service 配置模块。"""

import os


class Settings:
    """应用配置（优先读取环境变量）。"""

    # Backend
    backend_base_url: str = os.getenv("BACKEND_BASE_URL", "http://127.0.0.1:8080")
    request_timeout_seconds: float = float(os.getenv("BACKEND_TIMEOUT", "10"))

    # LLM（OpenAI 兼容协议）
    llm_base_url: str = os.getenv("LLM_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode")
    llm_api_key: str = (
        os.getenv("LLM_API_KEY")
        or os.getenv("DASHSCOPE_API_KEY")
        or os.getenv("OPENAI_API_KEY", "")
    )
    llm_model: str = os.getenv("LLM_MODEL", os.getenv("TONGYI_MODEL", "qwen3-omni-flash"))
    llm_timeout_seconds: float = float(os.getenv("LLM_TIMEOUT", "20"))

    # Embedding（OpenAI 兼容协议）
    embedding_base_url: str = os.getenv("EMBEDDING_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode")
    embedding_api_key: str = os.getenv("EMBEDDING_API_KEY") or os.getenv("DASHSCOPE_API_KEY", "")
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "text-embedding-v3")
    embedding_timeout_seconds: float = float(os.getenv("EMBEDDING_TIMEOUT", "20"))

    # Milvus 向量检索
    milvus_host: str = os.getenv("MILVUS_HOST", "localhost")
    milvus_port: int = int(os.getenv("MILVUS_PORT", "19530"))
    milvus_collection: str = os.getenv("MILVUS_COLLECTION", "video_embedding")
    milvus_top_k: int = int(os.getenv("MILVUS_TOPK", "8"))

    # Redis（会话摘要缓存）
    redis_host: str = os.getenv("REDIS_HOST", "localhost")
    redis_port: int = int(os.getenv("REDIS_PORT", "6379"))
    redis_db: int = int(os.getenv("REDIS_DB", "0"))
    redis_password: str | None = os.getenv("REDIS_PASSWORD")
    redis_summary_ttl_seconds: int = int(os.getenv("REDIS_SUMMARY_TTL", "1800"))


settings = Settings()
