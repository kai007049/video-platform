# Video Platform Backend

基于 Spring Boot 3 的视频平台后端服务，当前为 **纯 Java** 架构，不依赖独立 Python agent 服务。

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

## 主要能力

- 视频上传、删除、详情、播放统计
- 点赞、收藏、评论、弹幕、观看进度
- 搜索、热门搜索、搜索历史
- backend-only 推荐系统
- 私信、通知、系统消息
- 基于本地规则的标签推荐与投稿内容补全

## 快速开始

### 1. 初始化数据库

```bash
mysql -u root -p < src/main/resources/db/schema.sql
```

### 2. 启动依赖服务

- MySQL
- Redis
- MinIO
- Elasticsearch
- RocketMQ

### 3. 修改配置

主要配置文件：

`src/main/resources/application.yaml`

### 4. 启动项目

```bash
mvn spring-boot:run
```

默认端口：`8080`

## 说明

- 当前目录是 Java 后端独立模块
- 根目录与 `frontend/` 配合运行即可
- 搜索与投稿补全已收敛为 backend 内部实现
