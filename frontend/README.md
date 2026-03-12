# 哔哩哔哩仿制 - 前端

仿 B 站风格的视频平台前端，基于 Vue 3 + Vite 构建。

## 功能

- **首页**：视频瀑布流展示，分页加载
- **视频详情**：播放器、弹幕、评论、点赞
- **用户**：登录、注册
- **投稿**：上传视频、封面、标题、简介

## 技术栈

- Vue 3 (Composition API)
- Vue Router 4
- Pinia
- Axios
- Vite

## 开发

### 1. 安装依赖

```bash
cd frontend
npm install
```

### 2. 启动后端

确保后端服务运行在 `http://localhost:8080`（MySQL、Redis、MinIO 需已启动）。

### 3. 启动前端

```bash
npm run dev
```

前端将运行在 `http://localhost:5173`，API 请求会通过 Vite 代理转发到后端。

### 4. 生产构建

```bash
npm run build
```

构建产物在 `dist` 目录，可部署到 Nginx 等静态服务器。部署时需配置 API 反向代理：

```nginx
location /api {
    proxy_pass http://localhost:8080;
}
location /ws {
    proxy_pass http://localhost:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

## 目录结构

```
frontend/
├── public/
├── src/
│   ├── api/          # API 请求
│   ├── components/   # 通用组件
│   ├── router/       # 路由
│   ├── stores/       # Pinia 状态
│   ├── styles/       # 全局样式
│   └── views/        # 页面
├── index.html
├── package.json
└── vite.config.js
```
