# README Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite the root README so it works as both a GitHub showcase page and a complete getting-started guide with minimal-mode and full-mode startup instructions.

**Architecture:** Keep the work focused on one file: the root `README.md`. Build the new README from information already present in the repo — current README files, backend configuration, and architecture docs — so the published content stays accurate. The rewrite should use a three-layer structure: showcase summary first, capability/architecture explanation second, and step-by-step startup guidance last.

**Tech Stack:** Markdown, GitHub README conventions, Spring Boot configuration, Vue/Vite project metadata

---

## File Map

- **Modify:** `README.md` — root GitHub landing page and startup guide.
- **Reference:** `backend/README.md` — backend-specific startup notes and capability summary.
- **Reference:** `frontend/README.md` — frontend run commands and proxy notes.
- **Reference:** `backend/src/main/resources/application.yaml` — actual dependency list, ports, and config keys.
- **Reference:** `backend/src/main/resources/SYSTEM_ARCHITECTURE.md` — system architecture wording.
- **Reference:** `backend/src/main/resources/RECOMMENDATION_ARCHITECTURE.md` — recommendation terminology and current implementation scope.
- **Reference:** `frontend/package.json` — frontend scripts and stack.

### Task 1: Reframe the README opening for GitHub visitors

**Files:**
- Modify: `README.md:1-40`
- Reference: `backend/src/main/resources/SYSTEM_ARCHITECTURE.md:1-74`
- Reference: `frontend/package.json:1-24`

- [ ] **Step 1: Write the failing content check**

Use this checklist as the failing test for the opening section. The current README fails if any item below is missing from the first screenful:

```text
- A one-sentence project positioning statement
- A concise project value summary
- Business capability keywords
- Engineering highlight keywords
- A cleaner title than the current generic “Video Platform” opening
```

Expected current failure:
- The opening is informative but too plain for a public GitHub release.
- It does not clearly separate “what users can do” from “why this project is technically interesting”.

- [ ] **Step 2: Rewrite the README title and opening summary**

Replace the top of `README.md` with a structure like this:

```md
# Bilibili Video Platform

一个面向视频内容分发场景的全栈项目，参考 Bilibili 的产品形态，覆盖视频上传、播放互动、搜索推荐、私信通知与内容补全等核心链路。

> 这是一个适合用于 **课程设计 / 毕业设计 / Java 全栈练手 / GitHub 项目展示** 的完整型项目，强调“业务功能闭环 + 工程架构亮点 + 可本地启动体验”。

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
```

- [ ] **Step 3: Read the rewritten opening and verify it passes the content check**

Read: `E:/Java-Project/Bilibili/Video-Platform/README.md`

Expected:
- The first 30-40 lines clearly answer “这是什么项目” and “为什么值得看”
- Both business capability labels and engineering highlights are visible
- The wording stays grounded in current implementation, not aspirational claims

### Task 2: Rebuild the middle section around capabilities, highlights, and structure

**Files:**
- Modify: `README.md:40-120`
- Reference: `backend/src/main/resources/SYSTEM_ARCHITECTURE.md:41-120`
- Reference: `backend/src/main/resources/RECOMMENDATION_ARCHITECTURE.md:15-119`
- Reference: `backend/README.md:17-25`

- [ ] **Step 1: Write the failing content check for the capability layer**

Use this checklist:

```text
- README must separate business modules from architecture highlights
- README must describe recommendation/search scope accurately
- README must explain repository structure
- README must not mix startup steps into the middle explanation sections
```

Expected current failure:
- The current README mixes summary, startup, and implementation notes too early.
- The capability section is useful but not organized as a GitHub-facing landing page.

- [ ] **Step 2: Rewrite the middle section with explicit module and architecture groupings**

Add or rewrite these sections in `README.md`:

```md
## 功能总览

### 用户与内容链路
- 用户注册、登录、JWT 鉴权
- 视频上传、封面处理、视频在线播放
- 观看历史、播放进度、创作者作品管理

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
- 既能作为全栈练手项目，也适合用作课程设计或作品集展示。

### 工程亮点
- 后端采用 Spring Boot + MyBatis-Plus，职责划分明确。
- 使用 Caffeine + Redis 两级缓存优化热点读场景。
- 使用 RocketMQ 解耦视频处理、搜索同步、通知推送等异步流程。
- 使用 WebSocket 支撑实时消息通知。
- 搜索与推荐链路均已在 backend 内部收敛，不依赖独立 Python agent-service。

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

### Frontend
- Vue 3
- Vue Router 4
- Pinia
- Axios
- Vite

## 项目结构

```text
Video-Platform/
├── backend/     # Spring Boot 后端服务
├── frontend/    # Vue 3 + Vite 前端应用
├── docs/        # 设计文档与计划文档
└── README.md    # 项目主说明
```
```

- [ ] **Step 3: Read the middle section and verify the grouping is clean**

Read: `E:/Java-Project/Bilibili/Video-Platform/README.md`

Expected:
- A visitor can distinguish “功能总览”, “项目亮点”, “技术栈”, and “项目结构” at a glance
- Recommendation wording stays accurate: current implementation is backend-only, with recall/rerank concepts already present
- No startup commands appear before the startup section begins

### Task 3: Add dual-mode startup instructions with dependency tiers

**Files:**
- Modify: `README.md:120-220`
- Reference: `backend/src/main/resources/application.yaml:1-124`
- Reference: `frontend/README.md:20-59`
- Reference: `backend/README.md:26-54`

- [ ] **Step 1: Write the failing content check for startup guidance**

Use this checklist:

```text
- README must distinguish required vs optional dependencies
- README must include a minimal mode
- README must include a full mode
- README must mention concrete directories, commands, and ports
- README must explain what degrades when optional dependencies are missing
```

Expected current failure:
- The existing startup section is too short for public release.
- It lists dependencies but does not give a layered onboarding path.

- [ ] **Step 2: Add environment requirements and dependency tiers**

Insert sections like this before the command walkthrough:

```md
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

> 如果缺失增强依赖，项目不一定完全不可启动，但搜索、异步处理、对象存储或视频后处理能力会退化，建议发布体验使用完整模式。
```

- [ ] **Step 3: Write the minimal-mode startup section**

Add this structure to `README.md`:

```md
## 最小可运行模式

适合第一次 clone 后先把前后端跑起来，优先体验基础页面、接口链路与核心交互。

### Step 1. 初始化数据库

```bash
cd backend
mysql -u root -p < src/main/resources/db/schema.sql
```

### Step 2. 修改后端配置

至少检查 `backend/src/main/resources/application.yaml` 中以下配置：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.data.redis.host`
- `spring.data.redis.port`

### Step 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认端口：`8080`

### Step 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认端口：`5173`

### Step 5. 验证是否启动成功

- 浏览器打开 `http://localhost:5173`
- 前端页面能正常加载
- 后端接口可通过前端代理访问 `/api/**`

### 最小模式下的能力说明

此模式建议用于基础体验，部分对象存储、搜索、MQ 异步处理、视频后处理能力可能不完整。
```
```

- [ ] **Step 4: Write the full-mode startup section**

Add this structure immediately after minimal mode:

```md
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

`backend/src/main/resources/application.yaml`

重点关注：
- 数据库连接
- Redis 连接
- Elasticsearch `spring.elasticsearch.uris`
- MinIO `endpoint / access-key / secret-key / bucket-*`
- RocketMQ `rocketmq.name-server`
- ffmpeg `ffmpeg.path`

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

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

### 完整模式下可重点验证
- 视频上传与封面处理
- 搜索功能
- 通知/消息链路
- 推荐流体验
- 对象存储相关资源访问
```
```

- [ ] **Step 5: Read the startup section and verify it is executable**

Read: `E:/Java-Project/Bilibili/Video-Platform/README.md`

Expected:
- Commands are in chronological order
- Ports 8080 and 5173 are explicitly stated
- Minimal mode and full mode are clearly separated
- Required config keys are named directly from `application.yaml`

### Task 4: Add config guidance, FAQ, and document index

**Files:**
- Modify: `README.md:220-end`
- Reference: `backend/src/main/resources/CACHE_STRATEGY.md`
- Reference: `backend/src/main/resources/SYSTEM_ARCHITECTURE.md`
- Reference: `backend/src/main/resources/RECOMMENDATION_ARCHITECTURE.md`
- Reference: `backend/README.md`
- Reference: `frontend/README.md`

- [ ] **Step 1: Write the failing content check for the final section**

Use this checklist:

```text
- README must tell users what to edit before startup
- README must include troubleshooting / FAQ
- README must link to deeper docs already in the repo
- README must close with a clear project positioning statement
```

Expected current failure:
- The existing ending is too brief to support open-source release onboarding.

- [ ] **Step 2: Add key-config, FAQ, and document index sections**

Append sections like this to `README.md`:

```md
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

### 1. 后端能启动，但上传/封面处理失败
优先检查 MinIO 与 ffmpeg 配置是否正确。

### 2. 搜索接口不可用或没有结果
优先检查 Elasticsearch 是否启动，以及索引同步链路是否正常。

### 3. 消息、通知、异步处理体验不完整
优先检查 RocketMQ 是否已启动；未启动时部分异步能力会退化。

### 4. 前端接口报错或访问不到后端
确认后端是否运行在 `8080`，并检查前端 Vite 代理配置是否生效。

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
```

- [ ] **Step 3: Read the finished README end-to-end and verify tone and structure**

Read: `E:/Java-Project/Bilibili/Video-Platform/README.md`

Check these exact conditions:

```text
- The README starts with value, not commands
- The middle explains capability and architecture cleanly
- The startup section is split into minimal and full modes
- The ending includes config guidance, FAQ, and doc links
- No section claims unsupported functionality
```

Expected result: all five conditions are true.

### Task 5: Lightweight verification of the README against repo reality

**Files:**
- Verify: `README.md`
- Verify: `backend/src/main/resources/application.yaml`
- Verify: `frontend/package.json`
- Verify: `backend/README.md`
- Verify: `frontend/README.md`

- [ ] **Step 1: Re-check commands and config names against the source files**

Verify these items manually against the referenced files:

```text
- Backend run command is `mvn spring-boot:run`
- Frontend install command is `npm install`
- Frontend dev command is `npm run dev`
- Backend default port is 8080
- Frontend default port is 5173
- Config keys named in README actually exist in application.yaml
```

Expected: each item matches the repository exactly.

- [ ] **Step 2: Do a final wording pass for public release readability**

Edit `README.md` only if needed to ensure:

```text
- Headings are scannable
- Bullet lists are concise
- Startup steps are numbered and action-oriented
- Highlight wording is balanced (not too plain, not exaggerated)
- Chinese wording stays natural and consistent
```

Expected result: README reads like a polished open-source landing page, not an internal note dump.
