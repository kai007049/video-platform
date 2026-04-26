# Video Platform

一个面向视频内容平台场景的全栈项目，产品形态参考 Bilibili，重点展示后端在**业务闭环设计、缓存优化、搜索推荐、异步解耦**上的工程实现。

项目覆盖视频上传、播放互动、消息通知、搜索与推荐等核心链路，适合作为后端工程项目展示与面试讲解案例。

## 项目亮点

- 基于 **Spring Boot 3 + MyBatis-Plus + MySQL** 实现注册登录、投稿上传、播放互动、消息通知等核心业务链路
- 使用 **Redis + Caffeine** 构建两级缓存，优化视频详情等热点接口的访问性能
- 基于 **Elasticsearch** 实现搜索、热搜、搜索历史与索引重建能力
- 基于 **RocketMQ** 解耦通知、搜索同步等异步链路，降低核心流程耦合度
- 使用 **WebSocket** 支持实时消息推送与通知触达
- 提供 **seed generator**，便于快速构造演示数据并验证搜索、推荐等功能

## 核心业务能力

### 用户与内容
- 用户注册、登录、JWT 鉴权
- 视频上传、封面处理、播放、观看进度
- 用户主页、创作者主页、创作者中心

### 互动与社区
- 点赞、收藏、评论、弹幕、关注
- 私信、通知、系统消息、消息中心

### 搜索与推荐
- 标题 / 描述搜索、热门搜索、搜索历史
- 推荐流、热榜

## 架构与工程设计

### 后端工程主题
- **缓存设计**：视频详情使用 Redis + Caffeine 两级缓存，并对热点访问场景做本地缓存优化
- **搜索链路**：基于 Elasticsearch 支持检索、热搜与索引重建，覆盖搜索数据同步流程
- **推荐链路**：具备多路召回、打分、重排与曝光日志等基础推荐结构
- **异步解耦**：使用 RocketMQ 处理通知、搜索同步等异步任务
- **实时通信**：使用 WebSocket 支持消息与通知的实时推送
- **文件处理**：接入 MinIO 管理对象存储，结合 ffmpeg 支持视频与封面处理

### 架构文档
- [系统架构](docs/architecture/SYSTEM_ARCHITECTURE.md)
- [缓存策略](docs/architecture/CACHE_STRATEGY.md)
- [推荐架构](docs/architecture/RECOMMENDATION_ARCHITECTURE.md)
- [视频详情缓存拆分设计](docs/architecture/VIDEO_DETAIL_CACHE_SPLIT_DESIGN.md)

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
video-platform/
├── backend/              # Spring Boot 后端服务
├── frontend/             # Vue 3 + Vite 前端应用
├── docs/architecture/    # 架构与设计文档
└── README.md
```

## 快速开始

推荐使用 Docker 启动中间件，本机只安装 JDK、Maven、Node.js、npm 和 ffmpeg。

### 1. 准备基础软件

```bash
java -version      # JDK 17
mvn -version       # Maven 3.9+
node -v            # Node.js 18+
npm -v             # npm 9+
docker -v
docker compose version
```

如果你的 Docker 版本较旧，没有 `docker compose`，可以把下面命令里的 `docker compose` 换成 `docker-compose`。

### 2. 克隆项目并进入目录

```bash
git clone <your-repo-url>
cd Video-Platform
```

### 3. 启动中间件

```bash
cd backend/src/main/resources/docker
docker compose up -d
docker compose ps
```

默认会启动：

| 服务 | 端口 | 默认账号 |
| --- | --- | --- |
| MySQL | `3306` | `root / 123456` |
| Redis | `6379` | 无密码 |
| Elasticsearch | `9200` | `elastic / 123456` |
| Kibana | `5601` | 无 |
| MinIO API | `9000` | `admin / 12345678` |
| MinIO Console | `9001` | `admin / 12345678` |
| RocketMQ NameServer | `9876` | 无 |
| RocketMQ Broker | `10911` / `10909` | 无 |
| RocketMQ Dashboard | `8082` | 无 |

常用访问地址：

- MinIO Console：`http://localhost:9001`
- Kibana：`http://localhost:5601`
- RocketMQ Dashboard：`http://localhost:8082`
- Elasticsearch：`http://localhost:9200`

### 4. 初始化数据库

回到项目根目录后执行：

```bash
docker exec -i video_mysql mysql -uroot -p123456 < backend/src/main/resources/db/schema.sql
```

如果本机安装了 MySQL 客户端，也可以执行：

```bash
mysql -h 127.0.0.1 -P 3306 -uroot -p123456 < backend/src/main/resources/db/schema.sql
```

SQL 会创建并使用 `video_platform` 数据库。

### 5. 配置 ffmpeg

视频上传、封面抽帧等能力依赖 ffmpeg。

当前默认配置：

```yaml
ffmpeg:
  path: ${FFMPEG_PATH:ffmpeg}
```

Windows 示例：

```powershell
$env:FFMPEG_PATH="D:/your/path/ffmpeg.exe"
```

macOS / Linux：

```bash
brew install ffmpeg        # macOS
sudo apt install ffmpeg    # Ubuntu / Debian
export FFMPEG_PATH=ffmpeg
```

### 6. 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认地址：

- API：`http://localhost:8080`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

### 7. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认地址：

```text
http://localhost:5173
```

前端会通过 Vite 代理把 `/api` 请求转发到 `http://localhost:8080`，并将 `/ws` WebSocket 请求转发到后端。

### 8. 生成演示数据

如果页面数据较少，可以使用 seed generator：

```bash
cd backend
mvn spring-boot:run "-Dspring-boot.run.arguments=--seed.enabled=true --seed.mode=medium --seed.append=true --seed.search-reindex=false --seed.random-seed=42"
```

如需同时重建搜索索引：

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--seed.enabled=true --seed.mode=medium --seed.append=true --seed.search-reindex=true --seed.random-seed=42"
```

seed 执行完成后会自动退出，再重新启动普通后端服务：

```bash
mvn spring-boot:run
```

## 常见问题

### 端口冲突
- `3306`：本机已有 MySQL
- `6379`：本机已有 Redis
- `8080`：后端端口被占用
- `5173`：前端端口被占用
- `9000/9001`：MinIO 端口被占用

### ffmpeg 找不到
- Windows 检查 `FFMPEG_PATH` 是否指向 `ffmpeg.exe`
- macOS / Linux 执行 `ffmpeg -version` 确认命令可用

### MySQL 初始化失败
- 确认 `video_mysql` 容器已启动
- 查看 `docker compose logs -f mysql`
- 如本地数据卷已有旧数据，可停止容器后删除 `backend/src/main/resources/docker/mysql_data/` 再重启

### Elasticsearch 第一次启动较慢
- 第一次会安装 IK 分词插件
- 等 `http://localhost:9200` 可访问后再启动后端更稳

## 当前版本边界

- 完整体验依赖 MySQL、Redis、MinIO、Elasticsearch、RocketMQ、ffmpeg 等本地中间件
- 推荐、搜索、缓存等模块已经具备工程化结构，但仍有继续增强空间
- 当前版本更适合作为工程项目展示与学习案例，而不是直接作为生产系统部署

## 后续规划

- 推荐结果缓存与推荐反馈闭环
- 搜索结果缓存与搜索增强能力
- 多实例场景下的缓存一致性治理
- 分片上传、断点续传与 HLS 播放
- CI/CD、配置分层与可观测性完善
