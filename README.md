# Video Platform

一个面向视频内容场景的全栈项目，参考 Bilibili 的产品形态，围绕视频上传、播放互动、搜索、推荐、私信通知与自动化内容补全构建。项目当前采用 **frontend + backend** 双模块结构，其中后端为 **纯 Java** 实现，不依赖独立 Python agent 服务。

## 项目结构

- `backend/`：Spring Boot 后端服务，承载核心业务、数据访问、搜索、推荐、消息、缓存与异步处理
- `frontend/`：Vue 3 + Vite 前端应用，负责页面交互与展示

## 功能概览

### 平台核心能力
- 视频上传、封面处理、视频在线播放
- 点赞、收藏、评论、弹幕、观看进度保存
- 个人主页、创作者作品管理、后台基础管理能力

### 搜索与推荐
- Elasticsearch 关键词搜索
- 热门搜索、搜索历史
- 多路召回 + 打分 + 页面级重排的推荐系统
- `/search/hybrid` 接口保留，当前实现为 **ES-only**

### 自动补全与规则增强
- 投稿时基于后端本地规则补全标题、标签、分类
- 标签推荐接口基于纯 Java 规则实现
- 不依赖外部 agent-service 或 Python AI 服务

### 异步与缓存
- Caffeine + Redis 两级缓存
- RocketMQ 异步处理视频、封面、搜索索引、资源删除等链路
- MinIO 对象存储

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

## 快速启动

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

## 运行前说明

请根据本地环境调整以下依赖配置：

- MySQL
- Redis
- Elasticsearch
- RocketMQ
- MinIO
- ffmpeg（如需视频后处理）

## 当前实现说明

### 搜索系统
- 支持 Elasticsearch 关键词搜索
- 支持热门搜索与搜索历史
- 支持搜索索引全量重建
- `/search/hybrid` 保留接口，但当前不再依赖语义检索服务

### 推荐系统
- 当前推荐链路为 backend-only
- 核心流程：多路召回 -> 特征打分 -> 页面级重排
- 推荐相关特征由后端行为数据与规则逻辑维护

### 投稿补全
- 当标题、标签、分类、简介缺失时，由后端本地规则补全
- 标签置信度来源包含 `manual` 与 `rule`
- 不再依赖独立 agent-service

## 文档索引

- [backend/README.md](backend/README.md)
- [frontend/README.md](frontend/README.md)
- [系统架构说明](backend/src/main/resources/SYSTEM_ARCHITECTURE.md)
- [缓存策略说明](backend/src/main/resources/CACHE_STRATEGY.md)
- [推荐系统架构说明](backend/src/main/resources/RECOMMENDATION_ARCHITECTURE.md)

## 后续方向

- 完善推荐反馈闭环与效果评估
- 增加测试覆盖
- 完善部署与运维文档
- 继续增强基于后端规则与数据特征的搜索/推荐能力
