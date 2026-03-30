# Video Platform

一个按模块拆分的 B 站风格视频平台项目，当前包含 3 个平级模块：

- `backend/`：Spring Boot Java 后端
- `frontend/`：Vue 3 + Vite 前端
- `agent-service/`：FastAPI Python 智能辅助服务

## 目录结构

```text
Video-Platform/
  backend/
  frontend/
  agent-service/
```

## 启动方式

### 1. 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认端口：`8080`

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认端口：`5173`

### 3. 启动 Agent Service

```bash
cd agent-service
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

默认端口：`8001`

## 模块说明

- `backend` 负责用户、视频、评论、点赞、收藏、关注、消息、搜索等核心业务
- `frontend` 负责页面展示、交互与调用后端 API
- `agent-service` 负责问答检索、上传辅助、消息草稿等智能能力

## 建议

- 后续新增模块时继续保持“平级目录，职责独立”
- 部署时优先按模块分别构建和发布
- 根目录保留总说明、部署脚本和统一文档，不再放具体业务源码
