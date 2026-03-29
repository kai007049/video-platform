ASK_DEMO_CASES = [
    {
        "question": "帮我找 Java 后端实战视频",
        "expected_keywords": ["Java", "后端", "Spring"],
    },
    {
        "question": "想看 Redis 缓存入门",
        "expected_keywords": ["Redis", "缓存"],
    },
    {
        "question": "推荐一些 Vue 前端项目实战",
        "expected_keywords": ["Vue", "前端"],
    },
]

MESSAGE_DEMO_CASES = [
    {
        "scenario": "reply",
        "tone": "friendly",
        "latest_user_message": "你是人机吗",
        "must_not_include": ["我是AI", "语言模型"],
    },
    {
        "scenario": "support",
        "tone": "professional",
        "latest_user_message": "播放一直卡顿怎么处理",
        "must_include_any": ["排查", "步骤", "网络", "缓存"],
    },
]
