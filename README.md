# Bilibili Video Platform

一个面向视频内容分发场景的全栈项目，参考 Bilibili 的产品形态，覆盖视频上传、播放互动、搜索推荐、私信通知与内容补全等核心链路。

> 这是一个适合用于 **课程设计 / 毕业设计 / Java 全栈练手 / GitHub 项目展示** 的完整型项目，强调 **业务功能闭环 + 工程架构亮点 + 可本地启动体验**。

## 功能标签

- 视频上传与播放
- 点赞 / 收藏 / 评论 / 弹幕
- 搜索 / 热榜 / 搜索历史
- 推荐系统
- 私信 / 通知 / 消息中心
- 投稿自动补全

## 工程亮点

- 前后端分离（Vue 3 + Spring Boot）
- Caffeine + Redis 两级缓存
- RocketMQ 异步解耦
- WebSocket 实时消息推送
- Elasticsearch 搜索索引同步
- 多路召回 + 页面级重排推荐链路

## 功能总览

### 用户与内容链路
- 用户注册、登录、JWT 鉴权
- 视频上传、封面处理、视频在线播放
- 观看历史、播放进度、创作者作品管理
- 用户主页、创作者主页、头像上传

### 互动与社区能力
- 点赞、收藏、评论、弹幕
- 关注关系
- 私信、通知、系统消息、消息中心

### 搜索与推荐
- Elasticsearch 关键词搜索
- 热门搜索、搜索历史
- 多路召回 + 线性打分 + 页面级重排推荐链路
- 投稿时基于本地规则补全标题、标签、分类

## 项目亮点

### 业务亮点
- 核心视频平台链路相对完整，覆盖上传、播放、互动、消息与推荐。
- 除常规视频站核心流程外，还包含消息中心、投稿补全、搜索历史、创作者管理等扩展能力。
- 既能作为全栈练手项目，也适合用作课程设计或作品集展示。

### 工程亮点
- 后端采用 Spring Boot + MyBatis-Plus，职责划分明确，具备较好的 Java 项目结构可读性。
- 使用 Caffeine + Redis 两级缓存优化热点读场景。
- 使用 RocketMQ 解耦视频处理、搜索同步、通知推送、资源删除等异步流程。
- 使用 WebSocket 支撑实时消息通知。
- 搜索与推荐链路均已在 backend 内部收敛，不依赖独立 Python agent-service。
- 推荐系统具备多路召回、线性打分和页面级重排等完整思路，适合用作推荐系统工程化入门案例。

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
├── docs/        # 设计文档与计划文档
└── README.md    # 项目主说明
```

## 环境要求

建议本地环境：

- JDK 17
- Node.js 18+
- MySQL 8.x
- Redis 7.x
- Maven 3.9+（或可正常执行 Maven 命令的环境）
- npm 9+

## 依赖说明

### 最小可运行模式必需
- MySQL：存储业务主数据
- Redis：缓存与状态数据

### 完整功能模式建议补齐
- MinIO：视频、封面、头像、消息图片对象存储
- Elasticsearch：搜索索引与搜索能力
- RocketMQ：异步处理链路
- ffmpeg：视频后处理、封面抽帧等能力

> 如果缺失增强依赖，项目不一定完全不可启动，但搜索、异步处理、对象存储或视频后处理能力会退化。若你是准备正式体验项目完整效果，建议使用完整功能模式。

## 最小可运行模式

适合第一次 clone 后先把前后端跑起来，优先体验基础页面、接口链路与核心交互。

### Step 1. 初始化数据库

```bash
cd backend
mysql -u root -p < src/main/resources/db/schema.sql
```

### Step 2. 启动必需依赖

最小模式至少确保以下服务已启动：

- MySQL
- Redis

### Step 3. 修改后端配置

检查 `backend/src/main/resources/application.yaml` 中以下配置是否符合本地环境：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.data.redis.host`
- `spring.data.redis.port`

如果你暂时没有完整中间件环境，建议同时留意这些配置，它们在最小模式下可能需要你根据实际环境做取舍：

- `spring.elasticsearch.uris`
- `minio.endpoint`
- `rocketmq.name-server`
- `ffmpeg.path`

### Step 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认端口：`8080`

### Step 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认端口：`5173`

前端会通过 Vite 代理把 `/api` 请求转发到 `http://localhost:8080`，并将 `/ws` WebSocket 请求转发到后端。

### Step 6. 验证是否启动成功

- 浏览器打开 `http://localhost:5173`
- 首页可以正常加载
- 前端接口请求能通过代理访问后端
- 基础浏览、登录、页面跳转等能力可用

### 最小模式下的能力说明

此模式更适合快速跑通主站结构与基础交互，以下能力可能不完整或直接退化：

- 搜索能力依赖 Elasticsearch
- 视频/封面/头像/消息图片对象存储依赖 MinIO
- MQ 异步处理链路依赖 RocketMQ
- 视频封面抽帧、后处理链路依赖 ffmpeg

## 完整功能模式

适合想体验搜索、推荐、异步处理、对象存储与更完整业务链路的使用者。

### 需要准备的依赖
- MySQL
- Redis
- MinIO
- Elasticsearch
- RocketMQ
- ffmpeg

### 建议检查的配置项

配置文件：`backend/src/main/resources/application.yaml`

重点关注：
- 数据库连接：`spring.datasource.*`
- Redis 连接：`spring.data.redis.*`
- Elasticsearch：`spring.elasticsearch.uris`、用户名、密码
- MinIO：`minio.endpoint`、`access-key`、`secret-key`、`bucket-*`
- RocketMQ：`rocketmq.name-server`
- ffmpeg：`ffmpeg.path`

### 启动顺序建议
1. MySQL
2. Redis
3. MinIO
4. Elasticsearch
5. RocketMQ
6. backend
7. frontend

### 后端启动

```bash
cd backend
mvn spring-boot:run
```

默认端口：`8080`

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

默认端口：`5173`

### 完整模式下可重点验证
- 视频上传与封面处理
- 搜索功能
- 通知 / 消息链路
- 推荐流体验
- 对象存储相关资源访问
- WebSocket 实时推送体验

## 启动前建议重点检查的配置

首次启动前，建议至少检查这些配置是否符合本地环境：

- `backend/src/main/resources/application.yaml`
  - MySQL 地址、账号、密码
  - Redis 地址和端口
  - Elasticsearch 用户名、密码、地址
  - MinIO endpoint、access-key、secret-key、bucket 名称
  - RocketMQ name-server
  - ffmpeg 本地路径

## 常见问题

### 1. 后端能启动，但上传 / 封面处理失败
优先检查 MinIO 与 ffmpeg 配置是否正确。

### 2. 搜索接口不可用或没有结果
优先检查 Elasticsearch 是否启动，以及搜索索引同步链路是否正常。

### 3. 消息、通知、异步处理体验不完整
优先检查 RocketMQ 是否已启动；未启动时部分异步能力会退化。

### 4. 前端接口报错或访问不到后端
确认后端是否运行在 `8080`，并检查前端 Vite 代理配置是否生效。

### 5. 上传接口或静态资源链路异常
优先检查对象存储配置、bucket 是否存在，以及后端文件访问链路是否可用。

## 文档索引

- [backend/README.md](backend/README.md)
- [frontend/README.md](frontend/README.md)
- [系统架构说明](backend/src/main/resources/SYSTEM_ARCHITECTURE.md)
- [缓存策略说明](backend/src/main/resources/CACHE_STRATEGY.md)
- [推荐系统架构说明](backend/src/main/resources/RECOMMENDATION_ARCHITECTURE.md)

## 项目定位

这是一个偏工程实践导向的视频平台项目，适合用于：

- Java 全栈学习
- Vue + Spring Boot 联调练手
- 课程设计 / 毕业设计
- GitHub 作品集展示

## 后续方向

- 持续补充测试覆盖
- 继续优化 README 与开源体验
- 进一步完善推荐反馈闭环与效果评估
- 逐步增强完整模式下的部署和运维说明
