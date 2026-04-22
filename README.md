# Bilibili Video Platform

一个面向视频内容平台场景的全栈项目，参考 Bilibili 的产品形态，覆盖视频上传、播放互动、搜索推荐、私信通知与投稿管理等核心链路。

> 适合作为 **课程设计 / 毕业设计 / Java 全栈练手 / GitHub 作品集 / 面试系统设计项目** 展示。

## 项目定位

当前版本更适合定位为：

- **高完成度个人工程项目 / 开源展示项目**
- **适合演示和面试讲解的 V1 版本**
- **核心链路可运行，且具备继续演进空间**

它不是一个打磨到零配置生产可用的商业产品，但已经具备比较完整的业务闭环与明确的工程亮点。

## 核心能力

### 业务链路
- 用户注册、登录、JWT 鉴权
- 视频上传、封面处理、播放、观看进度
- 点赞、收藏、评论、弹幕、关注
- 用户主页、创作者主页、创作者中心
- 私信、通知、系统消息、消息中心
- 标题 / 描述搜索、热门搜索、搜索历史
- 推荐流、热榜

### 工程能力
- Spring Boot + MyBatis-Plus 后端分层
- Vue 3 + Pinia + Vue Router 前端
- Redis + Caffeine 两级缓存
- Elasticsearch 搜索索引与重建
- RocketMQ 异步解耦与轻量可靠性增强
- WebSocket 实时消息推送
- seed 数据生成器
- 批量行为模拟脚本（支持账号登录模式与 token 模式）

## 技术栈

### Backend
- Java 17
- Spring Boot 3
- MyBatis-Plus
- MySQL
- Redis
- Elasticsearch
- RocketMQ
- MinIO
- WebSocket
- Caffeine
- Swagger / OpenAPI

### Frontend
- Vue 3
- Vue Router 4
- Pinia
- Axios
- Vite
- Vite PWA Plugin

## 项目结构

```text
Video-Platform/
├── backend/     # Spring Boot 后端服务
├── frontend/    # Vue 3 + Vite 前端应用
├── docs/        # 辅助文档（公开版优先关注 README 与核心架构文档）
└── README.md    # 项目主说明
```

## 环境要求

建议本地环境：

- JDK 17
- Node.js 18+
- MySQL 8.x
- Redis 7.x
- Maven 3.9+
- npm 9+

## 依赖说明

### 轻量启动必需
- MySQL：业务主数据
- Redis：缓存与状态数据

### 完整功能模式建议补齐
- MinIO：视频、封面、头像、消息图片对象存储
- Elasticsearch：搜索索引与搜索能力
- RocketMQ：异步处理链路
- ffmpeg：视频后处理、封面抽帧等能力

> 当前仓库的目标是优先做到“依赖较少中间件也能尽量跑起来”，但这条路径没有覆盖所有依赖缺失组合的严格验证。若缺少 Elasticsearch、RocketMQ、MinIO、ffmpeg 等增强依赖，你可能还需要根据本地环境额外关闭或调整相关配置；如果你希望稳定体验完整链路，建议直接使用完整功能模式。

## 轻量启动尝试模式

### 1. 初始化数据库

```bash
cd backend
mysql -u root -p < src/main/resources/db/schema.sql
```

### 2. 启动基础依赖
- MySQL
- Redis

### 3. 修改后端配置
重点检查：
- `backend/src/main/resources/application.yaml`
- `spring.datasource.*`
- `spring.data.redis.*`

如暂时没有完整中间件环境，也请留意：
- `spring.elasticsearch.uris`
- `minio.endpoint`
- `rocketmq.name-server`
- `ffmpeg.path`

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认端口：`8080`

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认端口：`5173`

前端会通过 Vite 代理把 `/api` 请求转发到 `http://localhost:8080`，并将 `/ws` WebSocket 请求转发到后端。

## 完整功能模式

如果你想完整体验搜索、推荐、异步处理、对象存储与消息链路，建议补齐：

- MySQL
- Redis
- MinIO
- Elasticsearch
- RocketMQ
- ffmpeg

## 推荐测试数据生成

如果你想验证推荐算法、冷启动、热门召回和缓存行为，可以直接运行 backend 内置 seed 命令：

```bash
cd backend
mvn spring-boot:run "-Dspring-boot.run.arguments=--seed.enabled=true --seed.mode=medium --seed.append=true --seed.search-reindex=false --seed.random-seed=42"
```

说明：
- 该命令只会在显式传入 `--seed.enabled=true` 时执行
- 当前版本只支持 `append=true`
- 当前支持 `small` / `medium` / `large` 三种 mode
- 支持通过 `seed.random-seed` 固定随机结果
- 如果本地 Elasticsearch 可用，可以把 `--seed.search-reindex=false` 改成 `true`
- 运行完成后会输出作者、用户、视频、观看、点赞、收藏、关注数量摘要，并自动退出
- 第一版只生成 seed 占位视频 URL，不包含真实媒体资源

## 批量行为模拟脚本

如果你想快速批量模拟用户行为来测试推荐和热门，而不是手工逐个点击页面，可以使用：

### 账号登录模式

```bash
python backend/scripts/simulate_behavior_profiles.py \
  --base-url http://localhost:8080 \
  --profile technology \
  --accounts tech1:password:captchaKey:captchaValue \
  --show-recommended \
  --show-hot
```

### Token 模式

```bash
python backend/scripts/simulate_behavior_profiles.py \
  --base-url http://localhost:8080 \
  --profile technology \
  --tokens "$TOKEN_1" "$TOKEN_2" \
  --show-recommended \
  --show-hot
```

说明：
- `--accounts` 与 `--tokens` 二选一
- `--tokens` 模式会跳过 `/user/login`，直接调用 `/user/info` 获取真实用户名
- 推荐摘要按账号分别打印
- 热门榜摘要会在整个批次结束后统一打印一次

## 当前版本边界

当前版本已经完成并可对外表达的能力：

- 视频上传、详情、播放、互动、消息、通知、搜索、推荐等核心主链路
- 视频详情两级缓存与热点本地缓存
- 推荐系统的多路召回、打分、重排与曝光日志
- Elasticsearch 搜索、搜索历史、热搜、索引同步
- RocketMQ 驱动的异步解耦链路
- seed 数据生成与推荐行为模拟能力

当前更适合定义为“已设计 / 正在推进”的内容：

- 推荐结果缓存进一步稳定化
- 搜索结果缓存进一步稳定化
- 多实例缓存一致性治理
- 推荐反馈闭环与效果评估
- 搜索增强能力（如 query rewrite / agent search）

## 已知边界

- 完整体验仍依赖 MySQL、Redis、MinIO、Elasticsearch、RocketMQ、ffmpeg 等本地中间件
- 推荐、搜索、缓存等模块已经具备工程化结构，但仍保留继续增强空间，并非最终形态
- 项目更适合学习、展示和面试讲解，不建议直接理解为成熟生产级系统

## 核心文档入口

- [backend/README.md](backend/README.md)
- [frontend/README.md](frontend/README.md)
- [系统架构说明](backend/src/main/resources/SYSTEM_ARCHITECTURE.md)
- [推荐系统架构说明](backend/src/main/resources/RECOMMENDATION_ARCHITECTURE.md)
- [缓存策略说明](backend/src/main/resources/CACHE_STRATEGY.md)
- [行为模拟脚本说明](backend/scripts/README-behavior-simulator.md)

## 为什么这个项目适合放到简历 / GitHub

这个项目的价值不只是“做了一个视频网站”，而是：

- 有完整业务闭环
- 有搜索、推荐、缓存、异步解耦等工程主题
- 有 seed generator 与 behavior simulator 这种验证工具链
- 适合在面试里展开讲缓存、搜索、推荐、消息与系统演进思路

如果你把它作为简历项目，它更适合的定位是：

- **高完成度个人工程项目**
- **可演示的系统设计案例**
- **具备清晰演进路线的 V1 版本**