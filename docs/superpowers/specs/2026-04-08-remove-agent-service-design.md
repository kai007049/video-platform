# 移除 agent-service 并改为纯 Java 实现设计

## 1. 背景

当前项目仓库包含 `backend/`、`frontend/`、`agent-service/` 三个平级模块。虽然推荐链路已经支持 backend-only，但后端代码中仍存在对 `agent-service` 的直接依赖，包括标签推荐、语义搜索、投稿分析，以及视频语义索引相关的 MQ 消费逻辑。

本次调整的目标是：**彻底删除 `agent-service/` 模块，同时保证整个项目仍可正常运行，且后端不再依赖任何 Python 智能服务，统一改为纯 Java 实现。**

## 2. 目标

1. 删除仓库中的 `agent-service/` 目录及其文档引用。
2. 删除后端对 `agent-service` 的所有运行时依赖。
3. 将相关能力替换为纯 Java 的规则实现，而不是直接让接口失效。
4. 保证 backend 能独立构建、启动，并维持主流程可用。

## 3. 非目标

1. 不引入新的中间件或外部 AI 服务。
2. 不实现复杂 NLP、向量检索或 LLM 能力。
3. 不对无关业务模块做额外重构。
4. 不追求“语义搜索”等高级能力的等价替代，只保留稳定、可维护的基础版本。

## 4. 总体方案

本次改造采用“**删除远程 agent 依赖 + 纯 Java 规则替代**”方案。

核心思路如下：

1. 删除统一远程调用入口 `AgentClient`。
2. 将原本依赖 `agent-service` 的能力下沉到 backend 本地规则服务。
3. 搜索能力改为 ES-only，不再做语义融合。
4. 删除向量索引相关的 MQ 消费逻辑，避免保留无效异步链路。
5. 同步清理 README、架构说明等文档，使仓库结构与实际运行方式一致。

## 5. 架构调整

### 5.1 删除内容

后端删除以下类型的内容：

- `AgentClient` 及其 DTO 依赖链中仅服务于远程 agent 调用的部分
- `agent.service.url` 等配置引用
- 基于 `agent-service` 的远程 HTTP 调用逻辑
- 视频语义索引 / 删除相关的 MQ 调用链

仓库层面删除：

- 根目录 `agent-service/` 模块
- 根 README、backend README、架构文档中关于独立 Python agent 服务的说明

### 5.2 新的本地能力归属

后端新增一个纯 Java 的规则分析服务，统一承担以下职责：

- 标签推荐
- 投稿元数据补全
- 分类推断
- 简单标题补全

该服务不依赖外部 HTTP 服务，只依赖已有数据库数据（标签、分类）和用户提交文本（标题、简介）。

## 6. 模块级设计

### 6.1 标签推荐

当前入口：`/tag/recommend`

现状：
- controller 从数据库读取标签列表
- 调用 `AgentClient.recommendTags(...)`
- 再把标签名映射为标签 ID

调整后：
- controller 仍保留原接口，避免前端改动
- 使用本地规则服务根据 `title + description` 对现有标签名做匹配
- 规则采用：
  - 文本包含标签名
  - 去重
  - 保持稳定顺序
  - 限制最大返回数量
- 若无匹配结果，则返回空数组，不抛异常

这样可以保证标签推荐接口继续可用，但不再依赖外部服务。

### 6.2 搜索

当前实现：
- ES `multiMatch(title, description)` 获取一批结果
- `agent-service` 语义搜索获取另一批结果
- 再通过加权 merge 进行混排

调整后：
- 保留 ES `multiMatch(title, description)`
- 删除 `semanticSearch(...)` 调用
- 删除混排逻辑 `mergeSearchResults(...)`
- 分页直接基于 ES 命中结果

结果：
- 搜索页面仍然可用
- 不再依赖向量检索与语义搜索
- 搜索相关代码路径更简单、更稳定

### 6.3 投稿分析与元数据补全

当前实现中，投稿补全逻辑会调用 `agent-service` 返回：
- 建议标签
- 建议分类
- 摘要
- 生成标题

调整后改为纯 Java 规则：

#### description 处理
- 若用户已填写 description，则直接保留
- 若 description 为空，不再调用远程 summary，默认保留空字符串

#### tagIds 处理
- 若用户手动传了 tagIds，则优先使用手动值
- 若 tagIds 为空，则使用标题与简介匹配现有标签名
- 若仍未命中，则保留现有的关键词兜底思路
- 若最终仍无结果，则允许为空，避免写入脏数据

#### categoryId 处理
- 若用户手动传了 categoryId，则优先使用手动值
- 若 categoryId 为空，则优先根据命中的标签做分类映射
- 若标签映射失败，则使用标题/简介与分类名做关键词匹配
- 若仍失败，再回退到现有安全兜底策略

#### title 处理
- 若用户已填写 title，则保留手动标题
- 若 title 为空，则基于 description 或命中的标签生成简单标题
- 不引入复杂文本生成逻辑，只做可预测的规则拼接

#### featureSource
- 原 `"ai"` 调整为 `"rule"`
- 手工填写完整时继续使用 `"manual"`

### 6.4 自动标注

`VideoAutoTagServiceImpl` 当前依赖远程内容分析。

调整后：
- 改为复用同一套本地规则分析服务
- 保留“自动补充分类”的目标
- 不再要求必须有 AI 分析结果
- 若规则未命中，则静默跳过，不影响主流程

### 6.5 MQ / 异步链路

#### 视频语义索引消费者

当前 `VideoSemanticIndexConsumer` 负责消费视频索引消息并通知 `agent-service` 建立向量索引。

调整后：
- 直接删除该消费者
- 保留主业务上传链路，不再触发无意义的语义索引请求

#### 视频删除消费者

当前 `VideoDeleteConsumer` 在删除 MinIO 资源、清理缓存后，还会调用 `agent-service` 删除向量索引。

调整后：
- 保留 MinIO 删除与缓存失效
- 删除向量索引删除调用

## 7. 代码改动范围

预计涉及以下位置：

- `backend/src/main/java/com/bilibili/video/client/AgentClient.java`
- `backend/src/main/java/com/bilibili/video/controller/TagController.java`
- `backend/src/main/java/com/bilibili/video/service/impl/SearchServiceImpl.java`
- `backend/src/main/java/com/bilibili/video/service/impl/VideoCommandService.java`
- `backend/src/main/java/com/bilibili/video/service/impl/VideoAutoTagServiceImpl.java`
- `backend/src/main/java/com/bilibili/video/mq/VideoSemanticIndexConsumer.java`
- `backend/src/main/java/com/bilibili/video/mq/VideoDeleteConsumer.java`
- 相关 DTO / 配置 / 文档文件
- 根目录 `agent-service/`

## 8. 兼容性与风险控制

### 8.1 兼容性策略

- 尽量保留 controller 接口不变，减少前端联动成本
- 搜索接口保留原返回结构，仅取消语义增强
- 投稿接口保留原主流程，只替换内部补全来源

### 8.2 主要风险

1. 标签推荐命中率下降
2. 搜索结果相关性相比“语义混排”弱化
3. 投稿自动补全质量下降
4. 删除 `AgentClient` 后，遗漏调用点导致编译失败

### 8.3 控制措施

1. 全量 grep `AgentClient`、`agent-service`、`agent.service.url`，确保调用点清理干净
2. 将规则服务设计成统一入口，避免多个地方重复写逻辑
3. 保留已有 fallback 逻辑，优先做“稳态可用”而不是“复杂智能”
4. 通过编译和基础接口验证确认所有主路径正常

## 9. 验收标准

满足以下条件即可认为改造完成：

1. backend 可以成功编译
2. backend 可以成功启动
3. 标签推荐接口可正常返回结果或空数组，不报错
4. 搜索接口正常工作，且不再依赖语义搜索
5. 投稿流程可正常补全 metadata，不再调用 Python 服务
6. 删除视频流程不因 agent 逻辑报错
7. 仓库中不再存在 `agent-service` 运行依赖
8. 文档中不再把 `agent-service` 作为项目必需模块

## 10. 测试策略

重点验证以下场景：

1. `TagController` 标签推荐接口
2. `SearchServiceImpl.hybridSearch(...)` 的 ES-only 行为
3. 投稿时手工信息完整场景
4. 投稿时 title/tag/category 缺失的自动补全场景
5. 视频自动标注场景
6. 视频删除消息消费场景
7. Spring Boot 启动与核心 Bean 注入

## 11. 实施边界

本次只做“移除 agent-service 并纯 Java 替换”的必要改动：

- 不追加新接口
- 不重做推荐系统
- 不新增复杂搜索策略
- 不引入新的三方 AI SDK

目标是让项目从“三模块 + Python 智能服务”收敛为“**frontend + backend 两模块，其中 backend 纯 Java 自洽运行**”。
