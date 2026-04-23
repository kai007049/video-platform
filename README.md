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
└── README.md    # 项目主说明
```

## 从 0 到 1 启动项目

推荐使用 Docker 启动中间件，本机只安装 JDK、Maven、Node.js、npm 和 ffmpeg。这样最接近完整功能模式，也更适合别人 clone 后复现。

### 1. 准备基础软件

请先安装并确认版本：

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

### 3. 启动 Docker 中间件

项目提供了本地开发用的 Docker Compose 文件：

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

第一次启动 Elasticsearch 会安装 IK 分词插件，可能比其他容器慢一点。可以用下面命令观察日志：

```bash
docker compose logs -f elasticsearch
docker compose logs -f rocketmq-broker
```

常用访问地址：

- MinIO 控制台：`http://localhost:9001`
- Kibana：`http://localhost:5601`
- RocketMQ Dashboard：`http://localhost:8082`
- Elasticsearch 健康检查：`http://localhost:9200`

### 4. 初始化数据库

等 MySQL 容器启动完成后，回到项目根目录执行初始化 SQL：

```bash
cd ../../../../..
docker exec -i video_mysql mysql -uroot -p123456 < backend/src/main/resources/db/schema.sql
```

如果你本机安装了 MySQL 客户端，也可以用：

```bash
mysql -h 127.0.0.1 -P 3306 -uroot -p123456 < backend/src/main/resources/db/schema.sql
```

SQL 会创建并使用 `video_platform` 数据库。以后想重置本地库，可以重新执行同一个 `schema.sql`。

### 5. 配置 ffmpeg

视频上传、封面抽帧等能力依赖 ffmpeg。

Windows 推荐做法：

1. 下载 ffmpeg，例如 `ffmpeg-8.0.1-full_build`
2. 解压到本地目录
3. 确认 `ffmpeg.exe` 路径，例如：

```text
你的 ffmpeg.exe 绝对路径
```

当前默认配置已经使用这个路径：

```yaml
ffmpeg:
  path: ${FFMPEG_PATH:ffmpeg}
```

如果你的路径不同，可以在启动后端前设置环境变量。

PowerShell：

```powershell
$env:FFMPEG_PATH="D:/your/path/ffmpeg.exe"
```

macOS / Linux：

```bash
brew install ffmpeg        # macOS
sudo apt install ffmpeg    # Ubuntu / Debian
export FFMPEG_PATH=ffmpeg
```

### 6. 检查后端配置

主要配置文件是：

```text
backend/src/main/resources/application.yaml
```

默认值已经和 Docker Compose 对齐：

```text
MySQL:          localhost:3306 / video_platform / root / 123456
Redis:          localhost:6379
Elasticsearch:  http://localhost:9200 / elastic / 123456
MinIO:          http://localhost:9000 / admin / 12345678
RocketMQ:       localhost:9876
ffmpeg:         FFMPEG_PATH 或默认本机路径
```

这些都是本地 Docker 开发环境的默认值。如果要覆盖配置，请在终端里设置环境变量，或创建本地的 `application-local.yaml`。本地 `.env`、`application-local.yaml` 不会被 Git 跟踪。

### 7. 启动后端

打开一个新的终端：

```bash
cd backend
mvn spring-boot:run
```

后端默认地址：

- API：`http://localhost:8080`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

### 8. 启动前端

再打开一个新的终端：

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

前端会通过 Vite 代理把 `/api` 请求转发到 `http://localhost:8080`，并将 `/ws` WebSocket 请求转发到后端。

### 9. 生成推荐测试数据

如果页面数据较少，可以用内置 seed generator 生成一批演示数据：

```bash
cd backend
mvn spring-boot:run "-Dspring-boot.run.arguments=--seed.enabled=true --seed.mode=medium --seed.append=true --seed.search-reindex=false --seed.random-seed=42"
```

如果 Elasticsearch 已正常启动，并希望 seed 结束后同步重建搜索索引，可以改成：

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--seed.enabled=true --seed.mode=medium --seed.append=true --seed.search-reindex=true --seed.random-seed=42"
```

seed 命令执行完成后会自动退出，再重新启动普通后端服务：

```bash
mvn spring-boot:run
```

### 10. 常见问题

端口冲突：

- `3306`：本机已有 MySQL
- `6379`：本机已有 Redis
- `8080`：后端端口被占用
- `5173`：前端端口被占用
- `9000/9001`：MinIO 端口被占用

处理方式：关闭占用端口的服务，或修改 `docker-compose.yml` / `application.yaml` 里的端口。

ffmpeg 找不到：

- Windows 检查 `FFMPEG_PATH` 是否指向 `ffmpeg.exe`
- macOS / Linux 执行 `ffmpeg -version` 确认命令可用

MySQL 初始化失败：

- 先确认 `video_mysql` 容器已启动
- 再执行 `docker compose logs -f mysql` 查看启动日志
- 如果本地数据卷已有旧数据，可以停止容器后删除 `backend/src/main/resources/docker/mysql_data/` 再重启

Elasticsearch 第一次启动慢：

- 第一次会安装 IK 分词插件
- 等 `http://localhost:9200` 能访问后再启动后端更稳

MinIO bucket：

- `video`、`cover`、`avatar`、`message` bucket 会在上传时由后端自动创建

### 可选：轻量启动

如果只想先把基础接口跑起来，可以只准备 MySQL + Redis。但搜索、上传、消息异步、封面处理等链路会依赖 Elasticsearch、MinIO、RocketMQ、ffmpeg，缺少这些中间件时需要自行关闭或避开相关功能。对第一次体验项目的人，仍然建议使用上面的 Docker 完整模式。

## 当前版本边界

当前版本已经完成并可对外表达的能力：

- 视频上传、详情、播放、互动、消息、通知、搜索、推荐等核心主链路
- 视频详情两级缓存与热点本地缓存
- 推荐系统的多路召回、打分、重排与曝光日志
- Elasticsearch 搜索、搜索历史、热搜、索引同步
- RocketMQ 驱动的异步解耦链路
- seed 数据生成能力

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


## 为什么这个项目适合放到简历 / GitHub

这个项目的价值不只是“做了一个视频网站”，而是：

- 有完整业务闭环
- 有搜索、推荐、缓存、异步解耦等工程主题
- 有 seed generator 这种验证工具链
- 适合在面试里展开讲缓存、搜索、推荐、消息与系统演进思路

如果你把它作为简历项目，它更适合的定位是：

- **高完成度个人工程项目**
- **可演示的系统设计案例**
- **具备清晰演进路线的 V1 版本**

## 可完善 / 可扩展方向

下面这些方向比较适合作为后续迭代，也适合在面试中展开讲系统演进思路。

### 推荐系统

- 推荐结果缓存：将推荐流按用户、分页游标、曝光集合做短 TTL 缓存，降低重复刷新和高频访问压力
- 反馈闭环：引入点击、完播、跳出、停留时长等行为信号，优化召回权重和重排策略
- 多路召回治理：给热门、标签、分类、作者、最新内容分别做召回质量统计，避免单一路径主导推荐结果
- 去重与多样性：进一步控制同作者、同分类、同标签连续出现的问题，提高 feed 流体验
- 离线特征任务：把用户兴趣、视频热度、标签偏好沉淀为周期性特征，减少在线计算压力

### 搜索系统

- 搜索结果缓存：对高频 query 做结果缓存，并结合视频更新事件做失效
- 搜索排序优化：综合标题匹配、标签、播放量、互动量、新鲜度进行排序
- 搜索建议与纠错：支持 query suggestion、拼写纠错、同义词扩展
- 索引一致性：完善 MQ 失败补偿、重试和全量重建流程，降低数据库与 ES 不一致风险

### 缓存与性能

- 多级缓存治理：继续完善 Caffeine + Redis 的命中率统计、主动预热和失效策略
- 热点视频保护：对高播放、高互动视频做更细粒度的热点 key 保护
- 接口限流与降级：对推荐、搜索、上传、评论等高频接口做限流、熔断和降级兜底
- 慢查询治理：补充 SQL explain、索引检查和核心接口压测报告

### 消息与异步链路

- MQ 可观测性：增加消息状态面板，展示发送、消费、重试、死信和补偿情况
- 消息幂等增强：将业务幂等 key 设计沉淀为统一组件，降低重复消费风险
- 失败补偿任务：对搜索同步、通知推送、资源清理等链路做定时补偿

### 文件与视频处理

- 分片上传：支持大文件分片上传、断点续传、秒传和上传进度恢复
- 视频转码：接入多清晰度转码，如 360p / 720p / 1080p
- HLS 播放：将原始 mp4 播放升级为 HLS 切片播放，提高大视频播放体验
- 封面与审核：自动抽帧、封面候选、基础内容审核流程

### 安全与权限

- RBAC 权限模型：区分普通用户、创作者、审核员、管理员等角色
- 登录安全：增加刷新 token、验证码策略、登录失败限制和异地登录提醒
- 内容风控：对评论、弹幕、投稿标题和描述增加敏感词与频控策略

### 部署与工程化

- Docker 镜像化：补齐后端、前端 Dockerfile，形成一键部署流程
- CI/CD：增加 GitHub Actions，自动运行后端测试、前端测试和构建
- 配置分层：区分 `dev`、`test`、`prod` profile，避免本地配置和生产配置混杂
- 可观测性：接入日志 traceId、指标监控、接口耗时统计和错误告警
- API 文档完善：补充接口示例、鉴权说明、错误码说明和核心业务流程图
