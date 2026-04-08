# Backend 与 Agent-Service 集成说明

## 已实现的高优先级功能

### 1. 统一 AgentClient 客户端

**位置**: `backend/src/main/java/com/bilibili/video/client/AgentClient.java`

**功能**:
- 封装所有与 agent-service 的交互
- 提供同步标签推荐接口
- 提供语义搜索接口
- 提供异步问答任务接口

**使用示例**:
```java
@Autowired
private AgentClient agentClient;

// 标签推荐
TagRecommendResult result = agentClient.recommendTags(title, description, candidateTags);

// 语义搜索
SemanticSearchResult searchResult = agentClient.semanticSearch(query, 20);
```

### 2. 混合搜索（ES + AI 语义检索）

**Backend 接口**: `GET /search/hybrid?keyword=xxx&page=1&size=12`

**实现位置**:
- Controller: `SearchController.hybridSearch()`
- Service: `SearchServiceImpl.hybridSearch()`

**工作流程**:
1. ES 关键词搜索（快速召回 50 条）
2. Agent 语义检索（精准匹配 20 条）
3. 结果融合（ES 权重 0.6，语义权重 0.4）
4. 分页返回

**Agent-Service 接口**: `POST /agent/search/semantic`

**请求参数**:
```json
{
  "query": "搜索关键词",
  "top_k": 20
}
```

**响应**:
```json
{
  "video_ids": [1, 2, 3],
  "scores": [0.95, 0.88, 0.82]
}
```

### 3. 同步标签推荐

**Backend 接口**: `POST /tag/recommend`

**请求参数**:
```json
{
  "title": "视频标题",
  "description": "视频简介"
}
```

**响应**:
```json
{
  "code": 200,
  "data": [1, 2, 3]  // 标签ID列表
}
```

**优化点**:
- 使用统一的 AgentClient
- 直接调用同步接口，减少延迟
- 自动将标签名映射为标签ID

## 配置说明

### Backend 配置 (application.yaml)

```yaml
agent:
  service:
    url: http://localhost:8001
```

### Agent-Service 配置

确保以下服务正常运行:
- LLM 服务（通义千问）
- Embedding 服务
- Milvus 向量数据库
- Redis

## 测试步骤

### 1. 启动服务

```bash
# 启动 agent-service
cd agent-service
python -m uvicorn app.main:app --host 0.0.0.0 --port 8001

# 启动 backend
cd backend
mvn spring-boot:run
```

### 2. 测试混合搜索

```bash
curl "http://localhost:8080/search/hybrid?keyword=Java教程&page=1&size=10"
```

### 3. 测试标签推荐

```bash
curl -X POST http://localhost:8080/tag/recommend \
  -H "Content-Type: application/json" \
  -d '{"title":"Spring Boot入门教程","description":"从零开始学习Spring Boot"}'
```

## 核心优势

1. **统一客户端**: 所有 agent-service 调用都通过 AgentClient，便于维护和监控
2. **同步接口**: 标签推荐和语义搜索使用同步接口，响应更快
3. **混合搜索**: 结合关键词和语义检索，搜索结果更准确
4. **降级策略**: Agent 服务异常时自动降级，不影响主流程

## 后续扩展

可以继续添加:
- 视频推荐接口
- 内容审核接口
- 智能回复接口
