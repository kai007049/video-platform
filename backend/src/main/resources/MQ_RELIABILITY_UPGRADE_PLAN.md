# RocketMQ 可靠性升级改造清单

## 1. 文档目标

这份文档的目标不是把当前项目一次性改造成超重型的生产级 MQ 平台，而是基于现有实现，做一轮 **面试导向、收益明显、实现难度可控** 的升级。

升级后的目标是：

1. 面试中被问到 MQ 可靠性时，不会停留在“只会发消息、收消息”。
2. 能够清楚说明项目已经考虑了 **消息身份、幂等、重试、失败补偿、可观测性、异常分类、顺序边界** 等关键问题。
3. 保持改造规模可控，不优先引入过重的 outbox / CDC / 全量事务消息方案。

---

## 2. 消息可靠性目标定义

这一节必须先说清楚系统到底追求什么可靠性语义，否则后面的手段会显得没有边界。

### 2.1 当前目标语义

当前项目建议明确采用以下语义目标：

- **至少一次投递（At-Least-Once Delivery）**
- **消费端通过幂等控制，使业务效果接近 Exactly-Once**
- **通过重试、死信、补偿任务实现最终一致性（Eventual Consistency）**
- **不追求跨数据库与 MQ 的全局强一致**
- **不声称 RocketMQ 本身提供全局 Exactly-Once**

### 2.2 面试表达建议

如果被问：

> 你们做到的是 exactly once 吗？

推荐回答：

> 底层采用的是 RocketMQ 的至少一次投递语义，因此重复投递是允许发生的；
> 我们通过 eventId / bizKey 幂等、消费异常分类、失败重试和补偿任务，保证业务效果上的最终一致和近似 exactly-once，
> 但不把中间件本身描述成提供全局 exactly-once。

这句话能把语义边界说清楚，避免面试时回答发虚。

---

## 3. 当前实现现状

### 3.1 已有能力

当前项目中的 RocketMQ 已经接入了真实业务链路，而不是纯演示：

- 视频上传后异步做视频处理、封面处理、搜索同步
- 点赞 / 收藏 / 评论 / 弹幕 / 关注后发送通知或搜索同步消息
- 视频删除后异步删除对象存储资源和搜索索引
- 私信 / 系统通知通过 MQ 异步推送 WebSocket

当前代码中已经具备的基础可靠性能力包括：

1. **生产端本地重试**
   - `MQServiceImpl` 使用 `asyncSend + 本地定时重试`
2. **生产端失败留痕**
   - 重试失败后将记录写入 Redis dead-letter list
3. **消费端幂等去重**
   - `MqReliabilityService` 基于 `topic + payload hash` 做 Redis 去重
4. **消费者最大重试次数控制**
   - 多个 consumer 已配置 `maxReconsumeTimes = 5`

### 3.2 当前短板

当前实现还没有形成“大厂面试里更有说服力的可靠性闭环”，主要问题有：

1. **消息没有统一身份字段**
   - 缺少 `eventId / bizKey / traceId / occurredAt / version`
2. **消费幂等策略过于依赖 payload hash**
   - 技术上能用，但业务表达力弱
3. **生产失败只记录，不补偿**
   - dead-letter 只是 Redis list，没有自动恢复闭环
4. **生产操作与本地事务的一致性边界没有说清楚**
   - “数据库提交成功但消息未持久留痕”的窗口仍然存在
5. **缺少异常分类**
   - 目前更像“出错就重试”，没有区分可重试 / 不可重试 / 人工介入
6. **缺少统一可观测性**
   - 没有比较完整的 MQ 指标、告警、失败台账
7. **顺序性边界没有明确说明**
   - 哪些业务允许乱序、哪些要靠状态校验兜底，目前没有文档化
8. **部分 consumer 业务较薄**
   - 如 `DanmuConsumer` 还是 TODO 占位

### 3.3 当前方案的边界说明

这一点建议在文档里明确写出来，避免误以为当前方案已经“绝对可靠”。

当前阶段可以接受的现实边界是：

- 通过“业务成功后立即发消息 + 本地重试 + dead-letter + 定时补偿”提升可靠性
- **仍然接受极端情况下存在“业务成功但消息未持久留痕”的窗口**
  - 例如：数据库事务提交成功
  - 但 JVM 在 MQ 发送前或 dead-letter 持久化前崩溃
- 这说明当前方案是 **轻量增强版最终一致性方案**，而不是彻底解决本地事务与消息发送原子性的终极方案

下一阶段可选增强：

- 对关键链路引入 **本地消息表 / Outbox**
- 业务事务和消息事件记录同库提交
- 再由后台任务投递 RocketMQ

这样在面试里既能说明“当前做到了什么”，也能说明“当前还没覆盖什么”。

---

## 4. 升级原则

这轮升级遵循 5 个原则：

1. **先定义语义目标，再谈实现手段**
2. **优先补齐面试高频追问点**
   - 幂等、重试、最终一致性、死信、监控、顺序边界
3. **优先做低成本高收益项**
   - 统一消息元数据、幂等升级、补偿闭环
4. **避免重型方案**
   - 暂不优先做 outbox、CDC、全量事务消息
5. **尽量贴合当前代码结构**
   - 基于现有 `MQServiceImpl`、`MqReliabilityService` 和各个 consumer 演进

---

## 5. 建议抽象为“4 层可靠性闭环”

为了让整个方案结构更稳定、面试表达更清楚，建议把 RocketMQ 可靠性设计抽象为 4 层：

### 5.1 第一层：消息身份层

解决“这条消息是谁”的问题。

包括：

- `eventId`
- `bizKey`
- `traceId`
- `eventType`
- `version`
- `occurredAt`

### 5.2 第二层：消息投递层

解决“消息能不能到”的问题。

包括：

- producer 本地重试
- producer 失败留痕
- dead-letter / 补偿任务
- 最终失败台账
- 关键链路未来可升级 outbox

### 5.3 第三层：消费执行层

解决“消息重复了怎么办，失败了怎么办”的问题。

包括：

- **消息级幂等**：基于 `eventId`
- **业务级幂等**：基于 `bizKey + action` 或数据库唯一约束
- 异常分类：可重试 / 不可重试 / 人工介入
- 最大重试次数
- 最终失败记录

### 5.4 第四层：治理观测层

解决“怎么知道系统坏了，怎么排查”的问题。

包括：

- 结构化日志
- 指标统计
- 告警
- `traceId / eventId` 全链路串联
- dead-letter / compensation 台账
- 手动重放能力

这四层一旦建立，面试时不管问幂等、补偿、监控还是顺序边界，都能很自然地落到结构里回答。

---

## 6. 升级改造清单（按推荐优先级排序）

## 6.1 P0：统一消息元数据（强烈建议最先做）

### 改什么

给所有 MQ 消息补统一基础字段：

- `eventId`：消息唯一 ID
- `bizKey`：业务主键，如 `video:123`、`notify:user:456`
- `traceId`：链路追踪 ID
- `occurredAt`：事件发生时间
- `eventType`：事件类型
- `version`：消息版本
- 可选：`producerService`

### 为什么

这是所有可靠性设计的底座。

有了统一元数据后，后续这些能力都会更容易做：

- 消费幂等
- 死信排查
- 消息重放
- 链路追踪
- 结构化日志
- 版本兼容

### 版本兼容建议

`version` 不建议继续只写成“可选”，而建议明确落地：

- 每种消息体都增加 `version`
- Consumer 做向前兼容
- 新字段尽量追加，不随意删老字段
- 如果有重大语义变化，再升级为新的 `eventType` 或新 topic

### 实现难度

**低**

### 面试价值

**非常高**

### 建议涉及文件

- `backend/src/main/java/com/bilibili/video/service/impl/MQServiceImpl.java`
- `backend/src/main/java/com/bilibili/video/model/mq/VideoProcessMessage.java`
- `backend/src/main/java/com/bilibili/video/model/mq/SearchSyncMessage.java`
- `backend/src/main/java/com/bilibili/video/model/mq/NotifyMessage.java`
- `backend/src/main/java/com/bilibili/video/model/mq/VideoDeleteMessage.java`
- 其他 `backend/src/main/java/com/bilibili/video/model/mq/*.java`

---

## 6.2 P0：把幂等拆成“消息级幂等 + 业务级幂等”

### 改什么

升级 `MqReliabilityService` 的幂等策略，不再只说“payload hash 升级为 eventId / bizKey”，而是明确拆成两层：

#### 第一层：消息级幂等

目标：避免同一条消息重复消费。

建议优先基于：

- `eventId`

#### 第二层：业务级幂等

目标：避免等价业务操作重复生效。

建议基于：

- `bizKey + action`
- 或数据库唯一约束

例如：

- `video:123:delete`
- `user:456:follow:user:789`
- `notify:user:123:comment:456`

### 为什么

这是一个非常关键的层次提升。

因为：

- **消息级幂等** 解决的是“同一条消息重复投递”
- **业务级幂等** 解决的是“不同 eventId 但等价业务操作重复触发”

例如：

- 同一消息被 RocketMQ 重投，`eventId` 就能挡住
- 用户连点两次关注，可能是两个不同 `eventId`，这时要靠 `bizKey + action` 或数据库唯一约束挡住

payload hash 可以保留为 fallback，但不再作为主策略。

### 实现难度

**低**

### 面试价值

**非常高**

### 建议涉及文件

- `backend/src/main/java/com/bilibili/video/service/impl/MqReliabilityService.java`
- `backend/src/main/java/com/bilibili/video/mq/SearchSyncConsumer.java`
- `backend/src/main/java/com/bilibili/video/mq/NotifyConsumer.java`
- `backend/src/main/java/com/bilibili/video/mq/VideoProcessConsumer.java`
- `backend/src/main/java/com/bilibili/video/mq/VideoDeleteConsumer.java`
- 其他 consumer

---

## 6.3 P0：把 producer dead-letter 升级成补偿闭环

### 改什么

当前 `MQServiceImpl` 在发送失败后，会把记录写入 Redis：

- `mq:producer:dead-letter`

建议补成轻量闭环：

1. dead-letter 记录结构标准化：
   - `eventId`
   - `bizKey`
   - `topic`
   - `payload`
   - `attempt`
   - `reason`
   - `nextRetryAt`
   - `status`
2. 增加定时补偿任务：
   - 周期扫描失败消息
   - 重试发送
3. 增加补偿重试上限
4. 超限后标记 `FINAL_FAILED`
5. 最终失败进入台账，便于人工介入

### 为什么

目前只是“失败留痕”，还不能算真正的恢复闭环。

补上这一层后，你就可以完整讲：

- 本地发送失败先指数退避重试
- 重试失败进入 dead-letter
- dead-letter 再由补偿任务恢复
- 超限进入最终失败，等待人工介入

### 存储介质建议

#### 当前问题

Redis List 只适合临时兜底，不适合长期补偿系统，因为：

- 不方便按状态查询
- 不方便按 `nextRetryAt` 扫描
- 不方便多维过滤
- 不方便保留长期失败台账
- 不适合人工排查

#### 推荐的轻量方案

**短期方案：Redis ZSet + Hash**

- ZSet：按 `nextRetryAt` 排序，便于扫描待补偿消息
- Hash：保存 dead-letter 详情

这比 List 更适合延迟重试场景。

**中期方案：MySQL 补偿表**

建议表名示例：`mq_producer_task`

字段建议包括：

- `event_id`
- `biz_key`
- `topic`
- `payload`
- `status`
- `retry_count`
- `next_retry_time`
- `last_error`
- `created_at`
- `updated_at`

这套设计更适合：

- 可查
- 可分页
- 可人工干预
- 可做后台补偿台账
- 更容易向面试官证明“失败闭环”

### 实现难度

**中**

### 面试价值

**非常高**

### 建议涉及文件

- `backend/src/main/java/com/bilibili/video/service/impl/MQServiceImpl.java`
- `backend/src/main/resources/application.yaml`
- 建议新增：
  - `backend/src/main/java/com/bilibili/video/service/impl/MqProducerCompensationJob.java`
  - 或项目内统一 scheduler/job 目录

---

## 6.4 P1：明确 producer 侧与本地事务的一致性边界

### 改什么

在方案和实现中明确：

当前阶段：

- 先通过“业务成功后立即发送 + 本地重试 + dead-letter + 定时补偿”提升可靠性
- 明确接受极端情况下仍存在“业务成功但消息未持久留痕”的窗口

下一阶段可选增强：

- 对关键链路引入本地消息表 / outbox
- 业务事务和消息事件记录同库提交
- 再由后台任务异步投递 MQ

### 为什么

这是 MQ 可靠性里最经典的问题之一：

> 数据库事务提交成功了，但消息根本没发出去怎么办？

你当前方案能覆盖的是：

- `asyncSend` 失败 -> 本地重试 -> dead-letter -> 补偿

但覆盖不了的极端窗口是：

- 数据库成功提交
- 服务在 MQ 发送前崩溃
- 或消息未成功留痕到 dead-letter 就崩溃

这时当前方案不一定兜得住。

所以这部分必须明确写成“当前边界 + 下一阶段演进方向”，这样面试时反而更稳。

### 实现难度

**低到中**（如果先只做文档说明和关键链路事务后发送）

### 面试价值

**高**

### 建议涉及文件

- `backend/src/main/java/com/bilibili/video/service/impl/VideoCommandService.java`
- `backend/src/main/java/com/bilibili/video/service/impl/LikeServiceImpl.java`
- `backend/src/main/java/com/bilibili/video/service/impl/FavoriteServiceImpl.java`
- `backend/src/main/java/com/bilibili/video/service/impl/CommentServiceImpl.java`
- 其他调用 `mqService.send...` 的业务 service

---

## 6.5 P1：为 Consumer 增加三段式异常分类

### 改什么

建议把异常分类从两类升级为三类：

#### 1）可重试异常（Retryable）

典型场景：

- ES 短暂不可用
- MinIO 网络抖动
- WebSocket 通道瞬时异常
- 下游服务超时
- ffmpeg 临时处理失败

处理方式：

- 抛出异常
- 交给 RocketMQ 重试

#### 2）不可重试异常（Non-Retryable）

典型场景：

- `action` 不支持
- `entityType` 非法
- payload 缺关键字段
- 资源已永久不存在且无法恢复

处理方式：

- 记录失败
- 直接 ACK / 吞掉
- 进入失败台账，不再反复重试

#### 3）需人工介入异常（Manual-Intervention Required）

典型场景：

- ffmpeg 多次处理仍失败
- 消息内容合法，但关联资源长期缺失
- 补偿超过上限仍失败
- 数据状态异常，系统无法自动判定是否应继续处理

处理方式：

- 不再自动重试
- 标记 `FINAL_FAILED`
- 进入人工介入台账 / 告警 / 重放列表

### 为什么

这样整个消费失败策略就从“重试 / 不重试”升级为：

- 自动恢复
- 直接丢弃
- 人工兜底

这是面试里非常像大厂治理思路的表达方式。

### 实现难度

**低到中**

### 面试价值

**高**

### 建议涉及文件

- `backend/src/main/java/com/bilibili/video/service/impl/MqReliabilityService.java`
- `backend/src/main/java/com/bilibili/video/mq/SearchSyncConsumer.java`
- `backend/src/main/java/com/bilibili/video/mq/VideoProcessConsumer.java`
- `backend/src/main/java/com/bilibili/video/mq/VideoDeleteConsumer.java`
- `backend/src/main/java/com/bilibili/video/mq/NotifyConsumer.java`
- 其他 consumer

---

## 6.6 P1：增加统一 MQ 日志规范，并把 traceId / eventId 串起来

### 改什么

统一生产 / 消费日志字段，至少包含：

- `topic`
- `consumerGroup`
- `eventId`
- `bizKey`
- `traceId`
- `attempt`
- `status`
- `durationMs`
- `error`

同时明确：

- producer 日志打印 `eventId + traceId`
- consumer 日志打印 `eventId + traceId`
- 补偿日志也保留 `eventId + traceId`

### 为什么

这是最轻量的“全链路可追踪性增强”。

它能把：

- 用户请求
- DB 落库
- producer 发消息
- consumer 消费
- 失败补偿

串成一条链。

### 实现难度

**低**

### 面试价值

**中高**

### 建议涉及文件

- `backend/src/main/java/com/bilibili/video/service/impl/MQServiceImpl.java`
- `backend/src/main/java/com/bilibili/video/service/impl/MqReliabilityService.java`
- 所有 `backend/src/main/java/com/bilibili/video/mq/*.java`

---

## 6.7 P1：增加 MQ 基础指标与告警规则

### 改什么

补最基础的指标埋点，至少包括：

- producer send success/fail
- consumer success/fail
- consumer dedup skip
- dead-letter count
- compensation success/fail

在此基础上，明确告警规则：

- producer send fail 短时间突增
- consumer fail rate 持续升高
- dead-letter backlog 持续增长
- compensation 连续失败
- 某 topic 消费堆积严重

### 为什么

面试里的 MQ 可靠性不仅是“失败怎么办”，还包括：

- 怎么知道失败了？
- 怎么第一时间发现堆积？
- 怎么发现补偿任务没工作？

即使当前不接完整监控平台，也建议先把指标和告警规则设计清楚。

### 实现难度

**中**

### 面试价值

**中高**

### 建议涉及文件

- `backend/src/main/java/com/bilibili/video/service/impl/MQServiceImpl.java`
- `backend/src/main/java/com/bilibili/video/service/impl/MqReliabilityService.java`
- `backend/src/main/resources/application.yaml`

---

## 6.8 P1：建立消费失败台账与手动重放能力

### 改什么

为业务侧补一份消费失败记录（Redis 或 DB 都可），至少记录：

- `topic`
- `consumerGroup`
- `eventId`
- `bizKey`
- `payload`
- `errorMessage`
- `retryCount`
- `firstFailedAt`
- `lastFailedAt`
- `status`

同时预留：

- 手动重放能力
- 最终失败人工介入能力

### 为什么

RocketMQ broker 自身的重试 / DLQ 对业务研发排查并不够友好。

增加业务失败台账之后，可以：

- 快速定位消费失败消息
- 支持人工重放
- 支持失败统计
- 支持面试时描述“自动恢复 + 人工兜底”闭环

### 实现难度

**中**

### 面试价值

**中高**

### 建议涉及文件

- `backend/src/main/java/com/bilibili/video/service/impl/MqReliabilityService.java`
- 各个 consumer

---

## 6.9 P2：把 DanmuConsumer 从 TODO 升级为真实消费样例

### 改什么

当前 `DanmuConsumer` 还是占位：

- 只有日志
- TODO 注释说明还未真正处理业务

建议至少补成一个真实动作，例如：

- 弹幕统计
- 弹幕审核状态记录
- 敏感词检测结果落库
- 简单通知逻辑

### 为什么

现在这个 TODO 会削弱 MQ 模块的可信度。

补成真实消费后，面试时就不会显得 topic 是“只建了名字”。

### 实现难度

**低到中**

### 面试价值

**中**

### 建议涉及文件

- `backend/src/main/java/com/bilibili/video/mq/DanmuConsumer.java`
- `backend/src/main/java/com/bilibili/video/model/mq/DanmuMessage.java`
- 相关 service / mapper

---

## 6.10 P2：补充顺序性边界说明与状态校验兜底

### 改什么

明确在文档和实现中说明：

- 当前系统整体 **不追求全局顺序消费**
- 对顺序敏感的业务，不依赖消息天然顺序，而是用 **业务状态校验** 兜底

例如：

- 搜索同步按最终状态覆盖，而不是假设事件顺序严格可靠
- 视频删除事件消费时，删除资源 / 索引前先校验视频当前状态
- 通知场景接受一定程度乱序，但保证幂等与最终状态正确

### 为什么

因为面试官很可能会问：

- 如果先收到 update，再收到 delete 怎么办？
- 乱序了怎么办？

你不一定非要做严格顺序消息，但一定要说明边界和兜底策略。

### 实现难度

**低**

### 面试价值

**中高**

### 建议涉及文件

- `backend/src/main/java/com/bilibili/video/mq/SearchSyncConsumer.java`
- `backend/src/main/java/com/bilibili/video/mq/VideoDeleteConsumer.java`
- 其他顺序敏感 consumer

---

## 7. 暂不优先推荐的重型方案

以下方案不是不好，而是对当前项目阶段来说，**实现成本偏高，不符合“收益高、难度适中”的目标**：

1. **全量事务消息**
2. **Outbox + 定时扫描 + CDC**
3. **完整 broker DLQ 治理平台**
4. **复杂顺序消息设计**
5. **大规模自研 MQ 框架层**

如果后续真的要往更强的生产级可靠性继续演进，可以再考虑这些方案。

---

## 8. 推荐落地顺序

### P0：建议立即做

1. 统一消息元数据（含 `version`）
2. 幂等拆成消息级幂等 + 业务级幂等
3. producer dead-letter 补偿闭环

### P1：非常建议做

4. 明确 producer 与本地事务一致性边界
5. Consumer 三段式异常分类
6. 统一日志规范 + `traceId / eventId` 串联
7. 基础指标埋点 + 告警规则
8. 消费失败台账 + 手动重放能力

### P2：有时间再做

9. DanmuConsumer 补齐真实逻辑
10. 顺序性边界说明与状态校验兜底

---

## 9. 最小可交付版本（只做 3 件事时最值）

如果时间有限，只做下面 3 件事，面试收益最大：

### 9.1 统一消息元数据

这是可靠性设计的基础底座。做完后，整个消息链路才真正“有身份”。

### 9.2 幂等升级

把幂等从 payload hash 提升到 **消息级幂等 + 业务级幂等**，是最典型的“低成本高价值”升级。

### 9.3 dead-letter 补偿闭环

把“发送失败记下来”升级为“失败后可恢复”，这是当前可靠性方案里最重要的增强。

---

## 10. 升级完成后的面试表达建议

如果按本文档的 P0 + P1 核心项完成改造，可以这样描述：

> 这个项目里的 RocketMQ 不只是做异步解耦，我把它的可靠性设计拆成了 4 层：消息身份层、消息投递层、消费执行层和治理观测层。
> 
> 在消息身份层，每条消息都有统一元数据，包括 eventId、bizKey、traceId、eventType、version 和 occurredAt；
> 在投递层，生产端发送失败除了本地指数退避重试，还会进入 dead-letter，再由补偿任务做重投恢复；
> 在消费执行层，我们把幂等拆成了消息级幂等和业务级幂等，并对异常做可重试、不可重试、人工介入三类划分；
> 在治理观测层，我们补了统一日志、基础指标、告警规则和失败台账，支持按 eventId / traceId 排查消息全链路。
> 
> 底层语义上我们采用的是 RocketMQ 的至少一次投递，不把系统描述成全局 exactly-once；
> 而是通过幂等、重试、补偿和人工介入机制，保证业务效果上的最终一致和近似 exactly-once。
> 
> 对于数据库事务与 MQ 发送之间的一致性问题，我们也明确说明当前方案的边界：当前是轻量增强版最终一致性方案，下一阶段可对关键链路升级为本地消息表 / outbox。

这套表达方式会比“用了 RocketMQ 做异步解耦”更有说服力，也更容易应对面试官对 MQ 可靠性、幂等、重试、补偿、顺序性和可观测性的追问。

---

## 11. 结论

当前项目的 RocketMQ 使用已经具备一定基础，但还停留在“有可靠性意识、但没有形成体系闭环”的阶段。

按照本文档做一轮轻量升级后，可以把它提升到：

- **项目展示层面：更完整、更像真实工程**
- **面试表达层面：能回答语义目标、幂等、重试、补偿、监控、顺序边界等高频问题**
- **实现成本层面：保持在当前项目可承受范围内**

这正是当前阶段最值得追求的目标。