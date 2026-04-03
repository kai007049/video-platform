"""Agent Service 离线评估样例集。"""

# 问答评估样例
ASK_DEMO_CASES = [
    {
        "question": "帮我找 Java 后端实战视频",
        "expected_keywords": ["java", "后端", "spring"],
    },
    {
        "question": "想看 Redis 缓存入门",
        "expected_keywords": ["redis", "缓存"],
    },
    {
        "question": "推荐一些 Vue 前端项目实战",
        "expected_keywords": ["vue", "前端"],
    },
]

# 私信草稿评估样例
MESSAGE_DEMO_CASES = [
    {
        "scenario": "reply",
        "tone": "friendly",
        "latest_user_message": "你是人工吗？",
        "must_not_include": ["我是AI", "语言模型"],
    },
    {
        "scenario": "support",
        "tone": "professional",
        "latest_user_message": "播放一直卡顿怎么处理",
        "must_include_any": ["排查", "步骤", "网络", "缓存"],
    },
]

