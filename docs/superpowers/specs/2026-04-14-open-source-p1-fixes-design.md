# 开源前 P1 问题修复设计（1、2、4、5、6）

## 1. 背景

用户要求直接修复 `backend/OPEN_SOURCE_FIX_LIST.md` 中以下 P1 问题：

- P1-1 投稿接口没有真正启用 DTO 校验
- P1-2 投稿链路过度信任手填 `categoryId` / `tagIds`
- P1-4 消息中心未读总数可能重复统计 system 通知
- P1-5 清空会话后消息中心 `latestMessages` 仍可能出现旧消息
- P1-6 WebSocket 推送内容手拼 JSON，特殊字符可能导致前端解析失败

用户选择：

- 修复方式采用**方案 A**
- 需要**补必要测试**
- 参数校验失败时返回**统一 Result 错误体**
- 非法 `categoryId` / `tagIds` 采用**直接 400 拒绝**策略

## 2. 目标

本次改动目标如下：

1. 上传接口真正启用服务端参数校验，避免超长标题/简介绕过校验后打到数据库层。
2. 上传链路在手填完整分支中校验分类和标签是否存在，阻止脏数据入库。
3. 统一消息中心未读统计口径，消除 `system` 通知重复计入总数的问题。
4. 清空会话后，消息中心 `latestMessages` 不再显示当前用户视角已删除的消息。
5. 消息实时推送改为标准 JSON 序列化，确保特殊字符不会破坏前端解析。
6. 为上述行为补充必要自动化测试，覆盖核心风险点。

## 3. 非目标

本次不做以下事情：

1. 不处理 P1-3、P1-7、P1-8。
2. 不重构消息中心前端页面结构。
3. 不改变通知列表接口和系统通知分栏的现有展示方式。
4. 不新增数据库外键。
5. 不顺带做无关重构。

## 4. 方案选择

采用 **方案 A：最小改动且保证口径一致**。

### 4.1 P1-1 / P1-2：上传参数校验与脏数据拦截

- 在 `VideoController.upload` 中改为接收 `@Valid @ModelAttribute VideoUploadDTO`，让 DTO 校验真正生效。
- `VideoUploadDTO` 补充：
  - `title` 最大长度限制
  - `description` 最大长度限制
  - `tagIds` 最大数量限制
- `categoryId` 是否存在、`tagIds` 是否全部存在，不放在注解层做数据库访问校验，而是在 `VideoCommandService` 中做显式业务校验。
- 当用户走“手填完整”分支时：
  - `categoryId` 不存在 → 直接抛 `BizException(400, ...)`
  - 任一 `tagId` 不存在 → 直接抛 `BizException(400, ...)`
- 错误返回继续走现有 `GlobalExceptionHandler` + `Result.error(...)` 体系。

这样做的原因：
- DTO 负责静态边界校验；
- Service 负责依赖数据库的存在性校验；
- 分层清晰，且不会把 repository 依赖塞进校验注解。

### 4.2 P1-4：未读总数去重

统一口径定义为：

- `messageUnread`：私信未读数
- `notificationUnread`：全部通知未读数，**包含 system**
- `systemUnread`：系统通知未读数，仅作为分项展示
- `totalUnread = messageUnread + notificationUnread`

也就是说，`systemUnread` 不再额外加进总数。

这样改动最小，并且与当前：
- `NotificationServiceImpl.getUnreadCount()` 返回全部通知未读数
- 前端分别展示“通知”和“系统”分项

能够保持兼容。

### 4.3 P1-5：latestMessages 按用户视角过滤已清空消息

`MessageCenterServiceImpl.summary` 中查询 `latestMessages` 时，改为沿用会话可见性规则：

- 当前用户是发送方时，要求 `senderDeleted = 0`
- 当前用户是接收方时，要求 `receiverDeleted = 0`

并继续按 `createTime desc limit 5` 返回。

这样可与 [MessageServiceImpl.java](backend/src/main/java/com/bilibili/video/service/impl/MessageServiceImpl.java) 中已有的会话列表/历史消息视角规则保持一致，避免“我已清空但消息中心还显示旧消息”的口径冲突。

### 4.4 P1-6：WebSocket 推送改用对象序列化

- `MessageNotifyConsumer` 不再使用 `String.format` 手拼 JSON。
- 注入 `ObjectMapper`，把一个简单 payload 对象序列化为 JSON 字符串后再调用 `messageWebSocketServer.push(...)`。
- payload 保持当前字段结构：
  - `type`
  - `content`
  - `refId`

这样可以保证双引号、反斜杠、换行等内容被正确转义，不影响前端 JSON 解析。

## 5. 涉及文件

### 修改
- `backend/src/main/java/com/bilibili/video/controller/VideoController.java`
- `backend/src/main/java/com/bilibili/video/model/dto/VideoUploadDTO.java`
- `backend/src/main/java/com/bilibili/video/service/impl/VideoCommandService.java`
- `backend/src/main/java/com/bilibili/video/service/impl/MessageCenterServiceImpl.java`
- `backend/src/main/java/com/bilibili/video/mq/MessageNotifyConsumer.java`

### 可能新增/修改测试
- `backend/src/test/java/...` 下对应 controller / service / consumer 测试文件

### 保持兼容
- `backend/src/main/java/com/bilibili/video/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/bilibili/video/service/impl/NotificationServiceImpl.java`
- `frontend/src/views/MessageCenter.vue`
- `frontend/src/views/Layout.vue`

## 6. 测试设计

至少补以下测试：

### 6.1 上传接口校验测试

覆盖：
- `title` 超长时返回统一 `Result` 400
- `description` 超长时返回统一 `Result` 400
- `tagIds` 超出上限时返回统一 `Result` 400

优先用 controller 层测试验证 `@Valid` 已真正生效。

### 6.2 上传业务校验测试

覆盖：
- 手填完整且 `categoryId` 不存在时，上传被拒绝并返回 400 语义异常
- 手填完整且存在非法 `tagId` 时，上传被拒绝并返回 400 语义异常

优先用 service 单测验证业务分支与数据库存在性校验。

### 6.3 消息中心统计/过滤测试

覆盖：
- `totalUnread` 不再重复累计 `systemUnread`
- 清空会话后，`latestMessages` 不返回当前用户视角已删除的消息

适合用 service 单测。

### 6.4 WebSocket 推送 JSON 序列化测试

覆盖含以下内容的消息：
- 双引号
- 反斜杠
- 换行

断言推送给 `MessageWebSocketServer` 的字符串是可解析的合法 JSON，且内容未丢失。

## 7. 风险与控制

### 风险

1. `@ModelAttribute + @Valid` 改法若与当前 multipart 参数绑定方式不兼容，可能影响上传接口入参解析。
2. `tagIds` 如果前端传空数组或缺省，需要保持“允许缺省，由后端补全”的现有行为。
3. 未读总数口径修正后，若前端仍自己重复相加，也可能继续显示偏大。
4. `MessageNotifyConsumer` 若直接抛出 JSON 序列化异常，可能影响 MQ 消费稳定性。

### 控制措施

1. 保持 `video` / `cover` 文件参数仍用 `@RequestParam`，只把元数据字段收敛到 `@ModelAttribute VideoUploadDTO`。
2. `tagIds` 仅限制最大数量，不要求必填。
3. 只修正后端 summary 口径，并核对前端当前是否直接使用返回分项或本地重新累加。
4. 采用项目已存在的 `ObjectMapper` 注入方式，避免自建不一致序列化配置；必要时在消费方法中把序列化异常纳入运行时异常处理路径。

## 8. 验收标准

满足以下条件即可视为完成：

1. 上传接口对超长 `title` / `description` 返回统一 `Result` 400。
2. 上传接口对非法 `categoryId` / `tagIds` 返回统一 `Result` 400，且不会写入脏数据。
3. 消息中心 `totalUnread` 与各分项口径一致，不重复统计 `systemUnread`。
4. 清空会话后，消息中心 `latestMessages` 不再出现该会话已删除视角消息。
5. 私信/通知实时推送在内容包含特殊字符时仍能生成合法 JSON。
6. 对应自动化测试通过。

## 9. 结论

本次采用最小必要改动，优先修复开源前最容易暴露的问题：上传参数校验缺失、脏数据写入、消息中心统计口径错误、清空会话后残留消息、以及 WebSocket 推送 JSON 非法。方案尽量复用现有异常返回与前端展示口径，把风险控制在局部改动范围内。