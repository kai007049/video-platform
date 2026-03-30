# Video Platform Backend

基于 Spring Boot 3 的视频平台后端服务。

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
- 根目录的 `frontend/` 和 `agent-service/` 为并列模块
- 推荐后续将环境变量和部署脚本继续按模块拆分管理
