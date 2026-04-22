# Video Platform Frontend

基于 Vue 3 + Vite 的视频平台前端，负责首页推荐流、搜索页、视频详情页、创作者相关页面、消息中心等用户交互体验。

## 当前模块定位

`frontend/` 更适合作为：

- 视频平台页面层与交互层实现
- 后端接口能力的可视化承接层
- 推荐、搜索、消息、播放体验的前端落地点

它不是一个刻意追求复杂工程化的前端框架演示项目，而是服务于整个视频平台业务闭环的 UI 层。

## 技术栈

- Vue 3（Composition API）
- Vue Router 4
- Pinia
- Axios
- Vite
- Vite PWA Plugin

## 当前已落地页面 / 能力

- 首页推荐流 / 最新 / 热门切换
- 搜索结果页（视频 / 用户）
- 视频详情页（播放、弹幕、评论、点赞、收藏、关注）
- 用户主页 / 创作者主页
- 创作者中心
- 私信与消息中心
- 登录 / 注册 / 头像更新

## 与后端的关系

前端主要通过 `/api` 代理访问 backend：

- 搜索页使用后端 `/search`
- 首页推荐与热榜使用后端 `/video/recommended`、`/video/hot`
- 视频详情与播放统计使用后端 `/video/{id}`、`/video/{id}/play`、`/video/{id}/progress`
- 消息与通知通过 HTTP + WebSocket 组合完成

因此这个前端模块最核心的价值不是“展示 UI 技巧”，而是把后端的视频、搜索、推荐、消息和用户行为能力真正串成了一套可展示、可验证的用户体验。

## 开发

### 1. 安装依赖

```bash
cd frontend
npm install
```

### 2. 启动后端

确保后端运行在 `http://localhost:8080`。如果你要体验完整搜索与推荐能力，还需要准备：

- MySQL
- Redis
- Elasticsearch
- （可选）MinIO、RocketMQ、ffmpeg

### 3. 启动前端

```bash
npm run dev
```

默认端口：`5173`

### 4. 生产构建

```bash
npm run build
```

### 5. 前端最小验证

如已安装测试依赖，可运行：

```bash
npm run test
```

## 当前前端边界

### 已完成
- 推荐流、搜索页、视频详情页等核心页面可用
- 与后端推荐、搜索、播放、互动接口的联动已形成闭环
- 已补图片 fallback helper，避免缺失图片反复触发占位请求

### 仍在迭代
- 更细的 UI 打磨与一致性优化
- 更完整的前端测试覆盖
- 更强的异常状态 / 空态展示一致性

## 目录结构

```text
frontend/
├── src/
│   ├── api/          # API 请求封装
│   ├── assets/       # 静态资源
│   ├── components/   # 通用组件
│   ├── router/       # 路由配置
│   ├── stores/       # Pinia 状态管理
│   ├── utils/        # 前端工具函数
│   └── views/        # 页面级组件
├── package.json
├── vite.config.js
└── README.md
```

## 总结

这个前端模块的核心价值在于：

> 它不是孤立页面集合，而是把后端的视频、搜索、推荐、消息和用户行为能力真正串成了一套可展示、可验证的用户体验。