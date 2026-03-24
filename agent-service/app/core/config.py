import os


class Settings:
    """应用配置（优先环境变量）。"""

    backend_base_url: str = os.getenv("BACKEND_BASE_URL", "http://127.0.0.1:8080")
    request_timeout_seconds: float = float(os.getenv("BACKEND_TIMEOUT", "10"))

    llm_base_url: str = os.getenv("LLM_BASE_URL", "https://api.openai.com")
    llm_api_key: str = os.getenv("LLM_API_KEY") or os.getenv("OPENAI_API_KEY", "")
    llm_model: str = os.getenv("LLM_MODEL", "gpt-4o-mini")
    llm_timeout_seconds: float = float(os.getenv("LLM_TIMEOUT", "20"))


settings = Settings()
