# 修复消息系统 P0 问题设计

## 1. 背景

用户要求修复 `backend/OPEN_SOURCE_FIX_LIST.md` 中的两个 P0 问题：

1. P0-1：消息 WebSocket 可伪造 `userId`，存在越权收消息风险。
2. P0-2：通知事件链路未真正落库，通知功能未闭环。

同时要求：修复完成后，需要在 `backend/OPEN_SOURCE_FIX_LIST.md` 对应问题位置增加“修复记录”。

## 2. 目标

本次改动只解决两个最高优先级问题，并保留现有项目结构：

1. 让 `/ws/message` 的用户身份来自服务端鉴权结果，而不是客户端 query 参数。
2. 让 `NotifyConsumer` 真正完成通知落库，保证点赞/评论/收藏/弹幕类事件能进入通知系统。
3. 在问题清单文档中记录本次修复方式、涉及文件和验证要点。

## 3. 非目标

本次不做以下事情：

1. 不重构整个消息系统。
2. 不顺手修复消息中心未读统计、会话清空残留等 P1 问题。
3. 不扩展完整通知模板系统，只做最小可用的通知落库闭环。
4. 不收紧全局 CORS 策略，只处理消息 WebSocket 鉴权问题本身。

## 4. 方案设计

### 4.1 P0-1：WebSocket 身份绑定改为服务端握手鉴权

当前问题根因是 `MessageWebSocketServer` 在连接建立后直接从 query 中解析 `userId`。修复方案：

1. 新增一个 WebSocket 握手拦截器，例如 `MessageWebSocketAuthInterceptor`。
2. 在握手阶段读取：
   - `Authorization: Bearer <token>` 头；若无头，再尝试 query 中的 `token` 参数。
3. 使用现有 `JwtUtils` 校验 token，并结合 Redis 中的登录 token 做一致性校验，复用当前 HTTP 登录态判定规则。
4. 校验通过后，把真实 `userId` 写入 `attributes`。
5. `MessageWebSocketServer` 只从 `session.getAttributes()` 中读取 `userId`，彻底移除对 query 参数 `userId` 的信任。
6. 若鉴权失败，则握手阶段直接拒绝连接。

这样可以保持现有 WebSocket handler 结构基本不动，只是在连接建立前补上服务端鉴权。

### 4.2 P0-2：NotifyConsumer 接入 NotificationService

当前问题根因是 `NotifyConsumer` 只有日志，没有落库动作。修复方案：

1. 在 `NotifyConsumer` 中注入 `NotificationService`。
2. 为 `NotifyMessage(type, userId, targetId, content)` 建立一个最小通知映射逻辑：
   - `type` 作为通知类型
   - `userId` 视为通知接收人
   - `targetId` 作为关联对象 ID 参与文案拼接或补充上下文
   - `content` 作为通知正文来源
3. 调用 `notificationService.sendNotification(...)` 完成：
   - `notification` 表落库
   - Redis 未读数更新
   - `MessageNotifyMessage` 推送给消息 WebSocket
4. 对通知文案只做最小稳定实现，避免引入大范围业务重构。

### 4.3 文档修复记录

在 `backend/OPEN_SOURCE_FIX_LIST.md` 的 `P0-1` 与 `P0-2` 小节下新增“修复记录”，内容包括：

- 修复状态：已修复
- 修复时间
- 修复方式
- 涉及文件
- 验证建议

## 5. 预计修改文件

### 新增
- `backend/src/main/java/com/bilibili/video/ws/MessageWebSocketAuthInterceptor.java`

### 修改
- `backend/src/main/java/com/bilibili/video/config/WebSocketConfig.java`
- `backend/src/main/java/com/bilibili/video/ws/MessageWebSocketServer.java`
- `backend/src/main/java/com/bilibili/video/mq/NotifyConsumer.java`
- `backend/OPEN_SOURCE_FIX_LIST.md`

## 6. 验收标准

满足以下条件即可认为本次修复完成：

1. 未携带合法 token 时，`/ws/message` 无法建立消息连接。
2. 即使客户端伪造 `userId`，也不能冒充其他用户接收消息。
3. 点赞/评论/收藏/弹幕事件经过 `NotifyConsumer` 后，会生成 `notification` 记录。
4. 用户通知未读数会增长，并可被通知接口查询到。
5. `OPEN_SOURCE_FIX_LIST.md` 的 P0-1 / P0-2 下有明确修复记录。

## 7. 风险与控制

### 风险

1. WebSocket 握手鉴权与前端当前连接方式不兼容。
2. NotifyConsumer 接入后，如果通知接收人语义理解错误，可能把通知写给错误用户。

### 控制措施

1. 握手阶段同时兼容 `Authorization` 头和 query 中的 `token`，降低前端接入成本。
2. 保持 NotifyMessage 当前字段语义不变，只做最小映射，不扩展复杂模板逻辑。
3. 改动完成后至少编译 backend，并检查消息/通知相关链路未出现明显编译错误。

## 8. 结论

本次修复聚焦在“安全阻断项 + 功能闭环缺失”两个 P0 问题，采用最小改动方案：

- WebSocket 改成服务端握手鉴权绑定用户；
- 通知 MQ 消费端真正接入通知服务；
- 文档增加修复记录。

这是在不大改架构前提下，最适合当前仓库开源前收口的一步。