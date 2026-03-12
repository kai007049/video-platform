# Video Platform - 仿 Bilibili 视频平台后端

基于 Spring Boot 3 的仿 Bilibili 视频平台后端 API 系统。

## 技术栈

- Java 17
- Spring Boot 3
- MyBatis Plus
- MySQL 8
- Redis
- WebSocket（实时弹幕）
- JWT 认证
- MinIO 存储

## 快速开始

### 1. 数据库初始化

```bash
mysql -u root -p < src/main/resources/schema.sql
```

### 2. 启动依赖服务

- MySQL（默认 3306）
- Redis（默认 6379）
- MinIO（默认 9000）

### 3. 配置

修改 `application.yaml` 中的数据库、Redis、MinIO、JWT 配置。

### 4. 运行

```bash
mvn spring-boot:run
```

服务默认端口 8080，API 前缀 `/api`。

## API 文档

### 用户模块

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/user/register | 用户注册 |
| POST | /api/user/login | 用户登录 |
| GET | /api/user/info | 获取用户信息（需 Token） |

### 视频模块

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/video/upload | 上传视频（需 Token） |
| GET | /api/video/list | 视频列表（分页） |
| GET | /api/video/{id} | 视频详情 |
| POST | /api/video/{id}/play | 记录播放量（需 Token） |

### 评论模块

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/comment | 发表评论（需 Token） |
| GET | /api/comment/video/{videoId} | 视频评论列表 |

### 点赞模块

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/like/{videoId} | 点赞（需 Token） |
| DELETE | /api/like/{videoId} | 取消点赞（需 Token） |
| GET | /api/like/{videoId} | 是否已点赞（需 Token） |

### WebSocket 弹幕

```
ws://localhost:8080/api/ws/danmu/{videoId}?token={jwt_token}
```

连接后发送 JSON 格式弹幕：
```json
{"content": "弹幕内容", "timePoint": 10}
```

## 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 请求头

需认证的接口请携带：
```
Authorization: Bearer {jwt_token}
```
