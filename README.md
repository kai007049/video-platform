# Video Platform

一个面向视频内容场景的全栈项目，参考 Bilibili 的产品形态，围绕 **视频上传、播放互动、搜索、推荐、私信通知与 AI 辅助能力** 构建。项目采用前后端分离 + 独立智能服务的结构，适合作为全栈工程实践和简历项目展示。

## 项目简介

本项目以“视频平台”作为核心业务场景，包含以下能力：

- 视频上传、封面处理、播放与观看进度保存
- 点赞、收藏、评论、弹幕等互动能力
- 视频搜索、热门搜索、混合语义搜索
- 后端推荐系统（多路召回 + 打分 + 重排）
- 私信、通知、图片消息、WebSocket 实时能力
- AI 辅助的投稿分析、标签建议、消息草稿与语义检索增强

项目结构拆分为三个平级模块：

- `backend/`：Spring Boot 后端服务，承载核心业务与数据层
- `frontend/`：Vue 3 + Vite 前端应用，负责页面交互与展示
- `agent-service/`：FastAPI 智能服务，负责语义检索、内容分析和 AI 辅助能力

---

## 功能亮点

### 视频平台核心能力
- 视频上传、封面图处理、视频在线播放
- 点赞、收藏、评论、弹幕、观看进度保存
- 个人主页、创作者作品管理、管理后台基础能力

### 搜索与检索能力
- 基于 **Elasticsearch** 的视频搜索
- 搜索历史与热门搜索
- **关键词搜索 + 语义搜索融合** 的混合检索能力
- 后台支持**全量重建搜索索引**，便于恢复和维护搜索系统

### 推荐系统能力
- 设计并实现 **backend-only 推荐系统**
- 采用 **热门召回、新鲜召回、标签兴趣召回、分类偏好召回、作者偏好召回、运营推荐召回** 的多路召回策略
- 使用线性打分与页面级重排控制推荐相关性与多样性
- 支持曝光日志记录，便于后续分析推荐效果

### 缓存与异步架构
- 基于 **Caffeine + Redis** 的两级缓存体系，重点优化视频详情等热点场景
- 通过缓存失效、空值缓存、分布式锁、TTL 抖动等策略提升稳定性
- 基于 **RocketMQ** 实现搜索索引同步、视频处理、封面处理、资源删除等异步链路

### AI 辅助能力
- 上传时的标题 / 标签 / 分类建议
- 自然语言语义检索增强
- 消息草稿与问答辅助
- 内容分析与审核能力（作为独立 agent-service 提供）

---

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

### Agent Service
- Python 3
- FastAPI
- Uvicorn
- HTTPX
- Milvus
- OpenAI-compatible LLM / Embedding API

---

## 项目架构

整体采用三层模块化结构：

```text
用户请求
  -> frontend (Vue 3)
  -> backend (Spring Boot)
      -> MySQL / Redis / Elasticsearch / RocketMQ / MinIO
      -> agent-service (FastAPI)
          -> Embedding / Milvus / LLM
```

### 模块职责

#### `backend/`
负责：
- 用户、视频、评论、点赞、收藏、关注、私信、通知等业务逻辑
- 搜索索引同步
- 推荐系统
- 两级缓存与统计同步
- MQ 异步编排

#### `frontend/`
负责：
- 首页推荐流、视频详情页、投稿页、搜索页、消息中心、管理页面等前端交互
- 通过 REST API 与 backend 通信
- 通过 WebSocket 接收弹幕 / 消息等实时数据

#### `agent-service/`
负责：
- 语义搜索增强
- 投稿内容分析
- AI 标签/分类建议
- 消息草稿生成
- 内容审核辅助

---

## 仓库结构

```text
Video-Platform/
├─ backend/                 # Spring Boot 后端
│  ├─ src/main/java/
│  ├─ src/main/resources/
│  └─ README.md
├─ frontend/                # Vue 3 前端
│  ├─ src/
│  └─ README.md
├─ agent-service/           # FastAPI 智能服务
│  ├─ app/
│  └─ requirements.txt
└─ README.md                # 仓库总说明
```

---

## 快速启动

> 本项目依赖 MySQL、Redis、Elasticsearch、RocketMQ、MinIO 等基础服务。建议先阅读各模块 README，再进行本地启动。

### 1. 启动 backend

```bash
cd backend
mvn spring-boot:run
```

默认端口：`8080`

### 2. 启动 frontend

```bash
cd frontend
npm install
npm run dev
```

默认端口：`5173`

### 3. 启动 agent-service

```bash
cd agent-service
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

默认端口：`8001`

---

## 运行前说明

为了让项目顺利启动，需要根据本地环境调整配置：

- MySQL
- Redis
- Elasticsearch
- RocketMQ
- MinIO
- ffmpeg（如果需要视频后处理）
- LLM / Embedding API（如果启用 agent-service）
- Milvus（如果启用语义检索）

建议将这些配置按本地环境进行调整，不直接使用仓库中的默认值上线。

---

## 搜索 / 推荐 / AI 说明

### 搜索系统
- 支持 Elasticsearch 关键词搜索
- 支持热门搜索与搜索历史
- 支持语义检索增强
- 已补充搜索索引**全量重建入口**，便于 ES 恢复和维护

### 推荐系统
- 当前推荐链路已支持 **纯 backend** 模式运行
- 核心流程：多路召回 -> 特征打分 -> 页面级重排
- 后续可继续叠加 AI 增强，但不会依赖 agent-service 作为唯一主链路

### AI 服务
- 以独立服务形式提供，不与 backend 主链路强耦合
- 当前更适合作为“能力增强模块”，例如：
  - 投稿建议
  - 语义搜索增强
  - 消息草稿
  - 内容审核辅助

---

## 文档索引

项目中已经包含若干设计文档，可作为进一步阅读入口：

- [backend/README.md](backend/README.md)
- [frontend/README.md](frontend/README.md)
- [系统架构说明](backend/src/main/resources/SYSTEM_ARCHITECTURE.md)
- [缓存策略说明](backend/src/main/resources/CACHE_STRATEGY.md)
- [推荐系统架构说明](backend/src/main/resources/RECOMMENDATION_ARCHITECTURE.md)
- [Agent 集成说明](backend/src/main/resources/AGENT_INTEGRATION.md)

---

## 截图 / 演示

> 这里建议后续补充项目截图，例如：首页、视频详情页、投稿页、搜索页、消息中心、后台页面等。

可预留如下展示内容：
- 首页推荐流
- 视频播放页
- 投稿页
- 搜索页
- 消息中心
- 管理后台

---

## 后续规划

当前项目已经覆盖视频平台的主要核心链路，后续可以继续完善：

- 更完整的 AI 搜索增强与向量检索能力
- 更丰富的推荐反馈闭环与效果评估
- 更完善的测试体系与部署文档
- 更多运维与监控能力

---

## 说明

本项目更适合作为：
- 全栈工程练手项目
- 简历项目 / 面试项目
- 搜索、推荐、缓存、异步架构的综合实践
- 基于 agent 的能力增强实验底座

如果你正在阅读这个仓库，欢迎基于现有结构继续扩展与优化。