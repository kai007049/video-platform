# Video Platform Backend

基于 Spring Boot 3 的视频平台后端服务，当前为 **纯 Java** 架构，不依赖独立 Python agent 服务。这个模块承载了项目的核心业务逻辑、缓存设计、搜索、推荐、消息通知与异步处理能力，是整个项目工程亮点最集中的部分。

## 当前模块定位

`backend/` 更适合作为：

- 视频平台核心业务后端
- 推荐、搜索、缓存、MQ、消息链路的实现主体
- 面试中展开系统设计与工程优化讨论的重点模块

当前版本已经具备较完整的业务与工程链路，但仍保留继续增强推荐结果缓存、搜索结果缓存、反馈闭环、测试与部署能力的空间。

## 技术栈

- Java 17
- Spring Boot 3
- MyBatis Plus
- MySQL 8
- Redis
- Elasticsearch
- RocketMQ
- WebSocket
- MinIO
- Caffeine

## 当前已落地的主要能力

### 业务能力
- 视频上传、删除、详情、播放统计
- 点赞、收藏、评论、弹幕、观看进度
- 搜索、热门搜索、搜索历史
- backend-only 推荐系统
- 私信、通知、系统消息、消息中心
- 基于本地规则的标签推荐与投稿内容补全

### 工程能力
- 视频详情两级缓存与热点本地缓存
- 视频详情 `base + stats` 分层缓存设计
- RocketMQ 异步处理视频、封面、搜索同步、通知与资源清理
- RocketMQ 链路已补统一消息元数据、消息级/业务级幂等、producer dead-letter 补偿雏形、异常分类、消费失败台账与统一日志结构
- Elasticsearch 搜索索引同步与全量重建
- 推荐系统多路召回、打分、重排与曝光日志
- WebSocket 鉴权与实时消息推送
- seed generator 测试工具链

## 快速开始

### 1. 初始化数据库

```bash
cd backend
mysql -u root -p < src/main/resources/db/schema.sql
```

### 2. 启动依赖服务

建议至少准备以下依赖：

#### 轻量启动尝试模式
- MySQL
- Redis

> 这条路径的目标是尽量少依赖中间件先把后端跑起来，但当前还没有覆盖到所有依赖缺失组合的严格验证。若缺少 Elasticsearch、MinIO、RocketMQ、ffmpeg 等增强依赖，你可能仍需要根据本地环境额外关闭或调整相关配置。

#### 完整功能模式
- MySQL
- Redis
- MinIO
- Elasticsearch
- RocketMQ
- ffmpeg

### 3. 修改配置

主要配置文件：

- `src/main/resources/application.yaml`

重点检查：
- 数据库连接
- Redis 连接
- Elasticsearch 地址与认证
- MinIO endpoint / access-key / secret-key
- RocketMQ name-server
- ffmpeg 本地路径

### 4. 启动后端

```bash
mvn spring-boot:run
```

默认端口：`8080`

## Seed Generator

用于本地追加推荐测试数据：

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--seed.enabled=true --seed.mode=small --seed.append=true --seed.search-reindex=false --seed.random-seed=42"
```

推荐调试顺序：
1. 先用 `small` 验证流程
2. 再切到 `medium` 观察推荐与缓存表现
3. 仅在本地资源足够时再尝试更大的 mode 或 override 数量

参数与行为说明：
- 必须显式传入 `--seed.enabled=true`
- 当前版本只支持 `append=true`
- 当前支持 `small` / `medium` / `large` 三种 mode
- 支持通过 `--seed.random-seed` 固定随机种子，便于复现
- `--seed.search-reindex` 控制是否在结束前触发搜索重建
- 运行完成后会输出作者、用户、视频、观看、点赞、收藏、关注数量摘要并自动退出
- 第一版只生成 seed 占位视频 URL，不包含真实媒体资源

## 当前能力边界

### 已完成 / 已落地
- backend-only 推荐主链路
- Elasticsearch 搜索主链路
- 视频详情缓存体系
- 通知落库与消息 WebSocket 推送
- MQ 解耦的异步后置任务
- seed generator 已可用于本地推荐 / 热门验证

### 已设计 / 正在推进
- 推荐结果缓存
- 搜索结果缓存
- 更进一步的缓存命中率与一致性优化
- 推荐反馈闭环与效果评估


## 总结

如果只看 `backend/`，这个模块最核心的价值在于：

> 它不仅覆盖了视频平台主业务链路，还把缓存、异步解耦、搜索、推荐、消息通知等系统设计问题做成了一个可运行的工程化实现。

这也是这个模块最适合在面试中深入展开的原因。
