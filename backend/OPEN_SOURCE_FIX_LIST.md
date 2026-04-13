# 开源前问题修复清单

> 这份文档基于当前仓库的一轮只读审计整理，目的是帮助项目在开源到 GitHub 前，优先修掉会影响安全性、功能完整性和首次启动体验的问题。
>
> 结论：**当前版本不建议直接开源即对外宣传“可直接运行”**。建议至少先处理本文档中的 P0 / P1 项。

---

## 1. 总体结论

当前项目的主链路已经具备一定完整度，前后端也能通过编译构建，但从“开源可用性”和“别人拉下来能否顺利跑起来”的角度看，仍有几类明显问题：

1. **安全问题**：消息 WebSocket 存在明显越权风险。
2. **功能闭环问题**：通知事件链路没有真正落库，消息中心/通知功能不完整。
3. **数据质量问题**：投稿接口缺少关键参数校验，可能写入脏数据或直接返回 500。
4. **开源启动体验问题**：RocketMQ、ffmpeg、本地依赖配置对 fresh clone 用户不友好，README 说明不足。

---

## 2. P0：开源前必须修复

### P0-1 消息 WebSocket 可伪造 userId，存在越权收消息风险

**问题描述**

当前消息 WebSocket 连接没有基于登录态做服务端鉴权，而是直接从 URL query 中读取 `userId`，并把这个 `userId` 绑定到 WebSocket session。也就是说，客户端理论上可以自己伪造 `userId`，去接收别人的消息推送。

**证据位置**

- [MessageWebSocketServer.java:20-24](src/main/java/com/bilibili/video/ws/MessageWebSocketServer.java#L20-L24)
- [MessageWebSocketServer.java:46-52](src/main/java/com/bilibili/video/ws/MessageWebSocketServer.java#L46-L52)
- [WebSocketConfig.java:24-26](src/main/java/com/bilibili/video/config/WebSocketConfig.java#L24-L26)

**影响**

- 存在明显消息越权风险。
- 开源后非常容易被他人复现。
- 属于安全阻断项，不建议带着这个问题公开仓库。

**修复建议**

- WebSocket 用户身份必须来自服务端认证结果，不能信任 query 参数。
- 在握手阶段校验 token / session，把认证后的 userId 绑定到连接上下文。
- 不要允许客户端自行声明 `userId`。

**修复记录（2026-04-13）**

- 修复状态：已修复
- 修复方式：新增 WebSocket 握手鉴权拦截器，握手阶段从 `Authorization` 头或 query 中的 `token` 解析登录态，并将认证后的 `userId` 写入 `WebSocketSession.attributes`。`MessageWebSocketServer` 不再读取 query 中的 `userId`，只信任服务端握手阶段写入的身份信息。
- 涉及文件：
  - `src/main/java/com/bilibili/video/ws/MessageWebSocketAuthInterceptor.java`
  - `src/main/java/com/bilibili/video/config/WebSocketConfig.java`
  - `src/main/java/com/bilibili/video/ws/MessageWebSocketServer.java`
- 验证建议：未携带合法 token 时应无法建立 `/ws/message` 连接；伪造 `userId` 也不应冒充他人接收消息。

---

### P0-2 通知事件链路未真正落库，通知功能实际未闭环

**问题描述**

点赞、评论、收藏、弹幕等业务会发送 `NotifyMessage` 到 RocketMQ，但 `NotifyConsumer` 目前只打印日志，没有真正调用通知服务写入 `notification` 表，也没有完成站内通知下发。

**证据位置**

上游发送：
- [LikeServiceImpl.java](src/main/java/com/bilibili/video/service/impl/LikeServiceImpl.java)
- [FavoriteServiceImpl.java](src/main/java/com/bilibili/video/service/impl/FavoriteServiceImpl.java)
- [CommentServiceImpl.java](src/main/java/com/bilibili/video/service/impl/CommentServiceImpl.java)
- [DanmuServiceImpl.java](src/main/java/com/bilibili/video/service/impl/DanmuServiceImpl.java)

消费端未完成：
- [NotifyConsumer.java:25-29](src/main/java/com/bilibili/video/mq/NotifyConsumer.java#L25-L29)

通知能力虽然存在，但未接上：
- [NotificationServiceImpl.java:29-40](src/main/java/com/bilibili/video/service/impl/NotificationServiceImpl.java#L29-L40)

**影响**

- 用户做了点赞/评论/收藏/弹幕后，通知列表可能没有任何记录。
- 消息中心中与通知相关的展示容易异常或为空。
- 会给开源使用者造成“功能有接口但实际不可用”的印象。

**修复建议**

- 让 `NotifyConsumer` 真正完成通知落库。
- 明确通知接收人、通知模板、通知类型映射。
- 如需实时推送，可在落库后再走 WebSocket/站内消息推送。

**修复记录（2026-04-13）**

- 修复状态：已修复
- 修复方式：`NotifyConsumer` 已接入 `NotificationService`，消费点赞、评论、收藏、弹幕等通知事件时会真正写入 `notification` 表、更新未读数，并通过既有通知推送链路发送到消息 WebSocket。
- 涉及文件：
  - `src/main/java/com/bilibili/video/mq/NotifyConsumer.java`
- 验证建议：触发点赞/评论/收藏/弹幕后，应能在通知列表中看到新通知，且未读数会同步变化。

---

## 3. P1：强烈建议开源前修复

### P1-1 投稿接口没有真正启用 DTO 校验，超长 title/description 可能直接打出 500

**问题描述**

`VideoUploadDTO` 上定义了 `@Size`，但 controller 的 `upload` 方法没有使用 `@Valid` / `@Validated`，因此这些约束实际上不会生效。

**证据位置**

- [VideoController.java:52-69](src/main/java/com/bilibili/video/controller/VideoController.java#L52-L69)
- [VideoUploadDTO.java:14-18](src/main/java/com/bilibili/video/model/dto/VideoUploadDTO.java#L14-L18)
- [schema.sql:34-51](src/main/resources/db/schema.sql#L34-L51)

**影响**

- 超长 `title` 可能直接在数据库 insert 时失败，表现为 500。
- 超长 `description` 会把异常数据带入搜索、分析和日志链路。
- 开源后容易被误操作或恶意请求触发。

**修复建议**

- 给上传接口参数加 `@Valid`。
- 增加 title/description/tag 数量/categoryId 的服务端边界校验。
- 校验失败时返回明确 4xx，而不是让数据库异常暴露成 500。

---

### P1-2 投稿链路对手填 categoryId/tagIds 过于信任，可能写入脏数据

**问题描述**

当前“手填完整”的分支会直接信任前端传入的 `categoryId` 和 `tagIds`，没有做服务端存在性校验。若传入不存在的分类或标签，可能导致脏数据进入 `video`、`video_tag`、`video_tag_feature` 等表。

**证据位置**

- [VideoCommandService.java:245-260](src/main/java/com/bilibili/video/service/impl/VideoCommandService.java#L245-L260)
- [VideoCommandService.java:623-635](src/main/java/com/bilibili/video/service/impl/VideoCommandService.java#L623-L635)
- [schema.sql:34-51](src/main/resources/db/schema.sql#L34-L51)

**影响**

- 搜索、推荐、详情页可能基于错误分类或错误标签工作。
- 若未来补数据库外键，问题会从“静默脏数据”变成“直接插入失败”。

**修复建议**

- 服务端校验 `categoryId` 是否存在。
- 服务端校验 `tagIds` 是否全部存在。
- 非法分类/标签在入库前直接拦截。

---

### P1-3 上传后立即删除，与异步封面/视频处理存在竞态

**问题描述**

上传成功后会立即发送视频处理、搜索同步、封面处理等异步消息；但删除视频时没有取消这些后处理任务，导致存在“视频已删除，但异步任务仍继续执行”的窗口。

**证据位置**

- [VideoCommandService.java:136-141](src/main/java/com/bilibili/video/service/impl/VideoCommandService.java#L136-L141)
- [VideoCommandService.java:171-191](src/main/java/com/bilibili/video/service/impl/VideoCommandService.java#L171-L191)
- [VideoCoverProcessService.java](src/main/java/com/bilibili/video/service/impl/VideoCoverProcessService.java)
- [VideoPostProcessFallbackService.java](src/main/java/com/bilibili/video/service/impl/VideoPostProcessFallbackService.java)

**影响**

- 删除后仍可能生成封面对象。
- MinIO 可能残留无主文件。
- MQ 可能产生重复失败或重试噪音。

**修复建议**

- 给视频增加可判断的状态位（如 deleted/processing）。
- 异步任务执行前再次检查视频是否仍然有效。
- 删除时对后处理做更明确的幂等和状态拦截。

---

### P1-4 消息中心未读总数可能重复统计 system 通知

**问题描述**

`notificationUnread` 已经统计了用户所有未读通知；`systemUnread` 又单独统计 type=system 的未读数量；最终 `totalUnread = msgUnread + notifyUnread + systemUnread`，存在重复计算风险。

**证据位置**

- [MessageCenterServiceImpl.java:32-41](src/main/java/com/bilibili/video/service/impl/MessageCenterServiceImpl.java#L32-L41)
- [NotificationServiceImpl.java:62-69](src/main/java/com/bilibili/video/service/impl/NotificationServiceImpl.java#L62-L69)

**影响**

- 消息中心未读总数和分项未读数对不上。
- 前端徽标数值可能偏大。

**修复建议**

- 统一口径：要么 `notificationUnread` 不包含 system，要么 `totalUnread` 不再重复加 systemUnread。

---

### P1-5 清空会话后，消息中心 latestMessages 仍可能出现旧消息

**问题描述**

用户清空会话后，消息中心 recent message 的查询仍只按 `receiverId` 获取最近 5 条，没有过滤删除视角标记，因此可能继续展示已清空的旧消息。

**证据位置**

- [MessageCenterServiceImpl.java:43-46](src/main/java/com/bilibili/video/service/impl/MessageCenterServiceImpl.java#L43-L46)
- [MessageServiceImpl.java](src/main/java/com/bilibili/video/service/impl/MessageServiceImpl.java)

**影响**

- 用户主观感受会是“我都清空了怎么还有消息”。
- 会话列表、消息中心、详情页之间的行为口径不一致。

**修复建议**

- latestMessages 查询按当前用户视角过滤删除标记。
- 尽量统一消息中心和会话列表的数据口径。

---

### P1-6 WebSocket 推送内容手拼 JSON，特殊字符会导致前端解析失败

**问题描述**

消息推送 JSON 是通过 `String.format` 手工拼接的，如果内容中出现双引号、反斜杠、换行等字符，可能得到非法 JSON。

**证据位置**

- [MessageNotifyConsumer.java:34-38](src/main/java/com/bilibili/video/mq/MessageNotifyConsumer.java#L34-L38)

**影响**

- 消息已经写库，但实时推送前端解析失败。
- 出现“偶发消息不弹”的线上问题，且排查成本高。

**修复建议**

- 使用 Jackson / ObjectMapper 序列化对象，不要手工拼 JSON。

---

### P1-7 RocketMQ 对开源用户的启动体验不友好

**问题描述**

当前 backend 默认依赖 RocketMQ，但文档没有清楚说明：
- RocketMQ 是否可选
- 不启动 MQ 会坏哪些功能
- 最小启动方式是什么
- topic / consumerGroup 是什么

同时多个 listener 是强注册，fresh clone 用户如果没起 RocketMQ，backend 启动大概率会很不顺。

**证据位置**

- [application.yaml:68-72](src/main/resources/application.yaml#L68-L72)
- [backend/README.md:34-52](README.md#L34-L52)
- [MQServiceImpl.java:57-67](src/main/java/com/bilibili/video/service/impl/MQServiceImpl.java#L57-L67)
- [MQServiceImpl.java:112-117](src/main/java/com/bilibili/video/service/impl/MQServiceImpl.java#L112-L117)
- 各类 `@RocketMQMessageListener`：
  - `VideoProcessConsumer`
  - `VideoDeleteConsumer`
  - `SearchSyncConsumer`
  - `NotifyConsumer`
  - `MessageNotifyConsumer`
  - 其他 MQ consumer

**影响**

- 别人拉仓库后大概率不能“按 README 一次成功”。
- 就算 producer 有降级，consumer 仍可能导致启动失败或功能不完整。

**修复建议**

- 增加 `mq.enabled` 一类的条件开关。
- 至少支持“无 MQ 也能启动，只是异步能力退化”的模式。
- README 必须写清楚 MQ 的角色、最小启动方式、缺失时的退化行为。

---

### P1-8 ffmpeg 路径是作者本机绝对路径，不适合直接开源

**问题描述**

配置里直接写死了 Windows 本机 ffmpeg 路径。

**证据位置**

- [application.yaml:95-97](src/main/resources/application.yaml#L95-L97)

**影响**

- 其他开发者启动后处理链路时大概率直接失败。
- 容易让使用者误以为项目本身有 bug，而不是配置问题。

**修复建议**

- 改为环境变量或可配置示例值。
- README 明确写出 ffmpeg 是可选还是必需，以及如何配置。

---

## 4. P2：建议开源前至少验证 / 视情况修复

### P2-1 私信历史分页顺序需验证

**问题描述**

私信历史列表如果按 `createTime asc` 做分页，第一页拿到的可能是最早消息而不是最近消息。

**影响**

- 聊天窗口初始展示体验可能不符合用户预期。

**建议**

- 结合前端调用方式验证第一页到底返回哪一段消息。

---

### P2-2 发送私信缺少接收方存在性/内容合法性校验

**问题描述**

需确认私信发送是否允许：
- 发给不存在用户
- 空内容
- 超长内容
- 给自己发消息

**建议**

- 检查 controller / service / 数据库约束是否已兜底。
- 若未兜底，补齐服务端校验。

---

### P2-3 默认封面对象与自动封面对象语义混用

**问题描述**

当前默认封面、用户上传封面、自动抽帧封面的对象命名和兜底逻辑存在历史混用，短期还能兼容，但中长期容易引入边界 bug。

**建议**

- 统一封面对象命名规范。
- 明确“默认封面”到底是真对象，还是后端虚拟占位图。

---

### P2-4 视频删除后的外围引用清理还需要扩查

**问题描述**

视频删除已清理主链路关联表，但通知、消息、推荐缓存、部分外围引用是否完全收口，建议再做一轮排查。

**建议**

- 梳理所有引用 `videoId` 的表、缓存和异步链路，补一个完整删除清单。

---

## 5. README / 配置最少要补的内容

开源前 README 至少建议补这 5 类信息：

1. **依赖分级说明**
   - MySQL / Redis / MinIO / Elasticsearch / RocketMQ / ffmpeg 哪些是必需，哪些是可选。

2. **RocketMQ 最小启动方式**
   - 最好直接给 docker compose 启动方式。
   - 写清 nameserver、broker、dashboard 端口。

3. **无 RocketMQ 时的退化行为**
   - 哪些功能还能用，哪些功能会退化。

4. **topic / consumerGroup 清单**
   - 方便别人排查 MQ 问题。

5. **启动前必须修改的配置项**
   - 数据库、Redis、ES、MinIO、RocketMQ、ffmpeg 等。

---

## 6. 开源前最建议手测的 6 条用例

### 消息相关

1. **WebSocket 越权测试**
   - 不登录直接连接 `/ws/message?userId=目标用户ID`
   - 看是否能收到目标用户消息。

2. **通知闭环测试**
   - A 给 B 的视频点赞 / 评论 / 收藏 / 发弹幕
   - 检查 notification 表、通知列表接口、消息中心未读数、WebSocket 推送是否都正常。

3. **清空会话残留测试**
   - 清空和某人的私信会话后，看消息中心 recent message 是否仍残留。

### 投稿 / 上传相关

4. **非法分类 / 标签测试**
   - 提交不存在的 `categoryId` 和 `tagIds`
   - 看是 4xx、500，还是成功写脏数据。

5. **超长 title / description 测试**
   - title 传 300+ 字符，description 传大文本
   - 看是否被明确拦截。

6. **上传后立即删除测试**
   - 不传封面，走自动抽帧
   - 上传成功后立刻删除
   - 检查 MinIO 是否残留对象、MQ 是否报错重试。

---

## 7. 修复优先级建议

建议按这个顺序处理：

1. **修 WebSocket 鉴权问题**
2. **补通知消费闭环**
3. **补投稿接口参数校验**
4. **补 RocketMQ / ffmpeg / 外部依赖说明，或支持无 MQ 启动**
5. **修消息中心未读统计与会话残留问题**
6. **把手拼 JSON 改成对象序列化**
7. **补充删除后异步链路竞态保护**

---

## 8. 最终建议

如果你准备把这个项目公开到 GitHub，建议先把它定位成：

- **“个人学习 / 演示型完整项目”** 没问题
- 但如果想让别人觉得它是 **“clone 后较容易跑起来、功能闭环较完整、逻辑风险较低”** 的仓库，本文档里的 P0 / P1 最好先修掉

尤其是下面 4 件事，建议作为开源前最低门槛：

- 修掉 WebSocket 越权
- 补齐通知链路
- 补齐投稿校验
- 补清楚 README 和 MQ 启动说明
