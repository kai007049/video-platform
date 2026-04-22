# 推荐结果缓存实现计划

## Context

当前项目的推荐页已经具备完整的多路召回、打分、重排与曝光日志链路，但每次请求都会重新执行整条推荐计算流程。对于首页推荐流这类高频读取场景来说，这条链路属于典型的“高计算、重复多、短时间相对稳定”的结果型场景，因此非常适合引入短 TTL 的结果缓存。

同时，这类缓存不能简单粗暴地直接缓存完整 `VideoVO`，因为当前系统已经在推进 `video:base:v2 + video:stat:{id}` 的详情分层缓存，且用户态（liked/favorited/lastWatchSeconds）仍是按用户请求时动态补充。如果推荐结果直接缓存完整 `VideoVO`，会带来缓存对象过重、失效复杂、与现有缓存体系不一致的问题。

因此这次推荐结果缓存的目标不是“把推荐页彻底固定住”，而是：在一个很短的时间窗口内，避免同一用户或同一类用户重复跑完整推荐链路，同时仍保持推荐结果具备短周期变化能力。

## 推荐方案

### 缓存对象
优先缓存：

```text
重排后的分页 videoId 列表
```

而不是：

- 完整 `VideoVO`
- 原始候选全集

### 为什么选分页 ID 列表

- 复用现有 `video:base:v2 + video:stat:{id}` 组装逻辑
- 避免缓存完整聚合 VO 的失效复杂度
- 比只缓存原始候选集更省计算，因为不需要每次重新做整套分页切片与重排
- 对当前项目的改动范围最小，最容易落地

## 缓存 key 设计

### 游客推荐

```text
rec:guest:home:v1:window:{windowSize}
```

### 登录用户推荐

```text
rec:user:{userId}:home:v1:window:{windowSize}
```

### 推荐的 window 含义

这里不直接按 `page` 做 key，而是先缓存一个短时间窗口内的 **稳定推荐结果窗口**，例如前 50 条、前 100 条推荐结果的有序 `videoIds`。请求具体页时，再从这个窗口里切分页。

例如：

- `rec:guest:home:v1:window:50`
- `rec:user:1001:home:v1:window:50`

### 为什么用 window key，而不是 page key

- 推荐结果本质上是一个短时间稳定的有序结果窗口，不是彼此独立的多个分页结果
- 先缓存一个窗口，再按页切片，更贴近推荐系统的结果层语义
- 多页请求时可以复用同一份结果窗口，避免 page 级 key 过碎
- 后续如果要加实验参数或分桶，也更容易扩展

### 后续可扩展字段

如果后面要支持实验参数或分桶，可以继续在 key 中扩展：

- `scene`
- `strategy version`
- `bucket`

例如：

```text
rec:user:{userId}:home:v1:bucket:{bucketId}:window:50
```

### 首版建议

首版仍保持最小复杂度，建议只落地：

- `guest/user`
- `home`
- `v1`
- `window:{windowSize}`

## TTL 策略

### 游客推荐
- 建议 TTL：**30 ~ 60 秒**
- 默认建议先取 **60 秒**
- 可加少量抖动（例如 10%~20%）

原因：
- 游客推荐个性化弱
- 重复访问高
- 如果首页内容更新节奏更快，可收紧到 30 秒；如果主要目标是削峰，60 秒也完全合理

### 登录用户推荐
- 建议 TTL：**15 ~ 20 秒**
- 同样建议加少量抖动

原因：
- 用户行为影响更强
- 需要保持推荐结果能相对更快更新
- 但也没必要每次请求都完全重算

## 接入位置

### 主要接入文件
- `backend/src/main/java/com/bilibili/video/service/impl/RecommendationServiceImpl.java`
- `backend/src/main/java/com/bilibili/video/common/RedisConstants.java`

### 为什么接在 RecommendationServiceImpl
因为推荐页最终分页结果是在：

- 多路召回
- 特征打分
- 页面级重排
- 分页切片
- `VideoVO` 组装
- 曝光日志记录

这一整条链路里生成的。

`VideoQueryService.listRecommended()` 只是透传，不适合作为缓存主入口。

## 读路径设计

### 未命中时

```text
请求推荐页
  -> RecommendationServiceImpl 执行完整推荐链路
  -> 得到短时间稳定的推荐结果窗口（有序 videoIds / 可选 candidate 元信息）
  -> 写入推荐结果缓存
  -> 按当前 page/size 从窗口中切片
  -> 按切片后的 videoIds 组装 VideoVO
  -> 基于最终成功返回的结果记录曝光日志
  -> 返回结果
```

### 命中时

```text
请求推荐页
  -> 先查推荐结果缓存（window videoIds）
  -> 按当前 page/size 从窗口中切片
  -> 按切片后的 videoIds 保序加载 Video
  -> 走现有组装逻辑，补 base/stats/用户态
  -> 以最终成功组装并返回给前端的视频集合记录曝光日志
  -> 返回结果
```

## 与现有缓存体系的配合方式

推荐结果缓存只负责：
- “这个用户/游客在当前时间窗口内，有一个怎样的稳定推荐结果窗口（videoIds）”

真正返回某一页时：
- 先从结果窗口中切出当前页需要的 videoIds
- 再组装这一页真正返回给前端的视频结果

- `video:base:v2:{id}`
- `video:stat:{id}`
- 用户态动态补充

因此推荐缓存与视频详情缓存是上下两层关系：

### 结果层缓存
- 推荐结果窗口（window videoIds）

### 内容层缓存
- 视频详情 base + stats

这样两层职责清晰，不互相替代。

## 曝光日志处理策略

当前推荐服务返回前会写曝光日志，调用：

- `recExposureLogService.logRecommendationExposureBatch(...)`

### 首版处理建议

首版仍然保持曝光日志存在，但要保证：

> **曝光日志必须基于最终成功组装并实际返回给前端的视频集合记录**。

也就是说，不能直接拿缓存里的原始 `videoIds` 就记曝光日志，而是要等：

1. 当前 page/size 已从缓存窗口中切片完成
2. 对应视频已成功加载
3. 已完成最终 `VideoVO` 组装

之后再记录曝光日志。

### 为什么必须这样做

因为缓存窗口中的原始 `videoIds` 只是候选结果，实际返回给前端时可能发生：

- 某条视频已不可用
- 某条视频补充失败
- 某些内容被过滤掉

曝光日志必须和“用户真正看到的内容”保持一致，否则后续分析曝光、点击、排序效果时会失真。

如果当前日志强依赖 `finalScore / recallChannels / strategyVersion`，可以采用下面两种策略之一：

#### 方案 A（推荐首版）
缓存分页结果时，同时缓存简化的 candidate 元信息，例如：
- `videoId`
- `finalScore`
- `channels`

这样命中缓存后仍可复用曝光日志记录逻辑。

#### 方案 B（更简化）
命中缓存时，曝光日志退化为只记录 `videoId` 级别曝光，不强求完整 score/channel 信息。

首版建议优先选 **方案 A**，因为更接近当前推荐链路已有能力。

## 如何避免“推荐每次都一样”的体验

推荐缓存并不意味着推荐结果永远固定。

### 当前建议

- 同一个 TTL 窗口内允许结果稳定
- TTL 到期后重新计算，结果自然发生变化
- 登录用户 TTL 更短，保证行为反馈更快

### 后续可选优化（本次不做）

- 对缓存结果加入轻量打散（只打散分数接近的视频）
- 基于曝光日志对最近已曝光内容做降权
- 游客按时间桶或设备指纹分桶，避免所有游客完全一致

## 失效策略

### 首版建议：以 TTL 为主
推荐结果本身不是强一致业务数据，因此不建议对所有行为都做精确删缓存。

#### 游客推荐
- 主要靠 TTL 自然过期

#### 登录用户推荐
- 主要靠 TTL 自然过期
- 对强语义行为可选做用户级失效，例如：
  - 点赞
  - 收藏
  - 评论
  - 观看完成

### 首版建议取舍
为了控制复杂度，首版推荐缓存实现建议：
- 先不在每个行为里显式删推荐缓存
- 主要依赖短 TTL
- 只有在效果不理想时，再补用户级失效

## 最小改动范围

### 必改文件
- `backend/src/main/java/com/bilibili/video/service/impl/RecommendationServiceImpl.java`
- `backend/src/main/java/com/bilibili/video/common/RedisConstants.java`

### 可复用文件 / 逻辑
- `backend/src/main/java/com/bilibili/video/service/impl/VideoCacheServiceImpl.java`
- `backend/src/main/java/com/bilibili/video/service/impl/VideoViewAssembler.java`
- `backend/src/main/java/com/bilibili/video/service/impl/VideoQueryService.java`（保序加载视频的思路可复用）
- `backend/src/main/java/com/bilibili/video/service/impl/RecExposureLogServiceImpl.java`

## 实施步骤

### Step 1
在 `RedisConstants.java` 中增加推荐结果缓存 key 前缀与 TTL 常量。

### Step 2
在 `RecommendationServiceImpl` 中新增：
- 游客推荐 key 构造
- 登录用户推荐 key 构造
- 推荐结果缓存读写逻辑

### Step 3
缓存内容先采用：
- 推荐结果窗口 `videoIds`
- 可选：`total`
- 可选：用于曝光日志的简化 candidate 元信息

### Step 4
命中缓存后：
- 先按当前 `page/size` 从窗口中切片
- 再按切片后的 ID 顺序加载 `Video`
- 调用现有 `VideoViewAssembler` 组装 `VideoVO`

### Step 5
曝光日志必须基于“最终成功组装并返回给前端的结果集合”记录，而不是直接对缓存窗口原始 `videoIds` 记日志。

## 验证方案

### 功能验证
- 同一个用户短时间内重复打开推荐页，应命中推荐结果窗口缓存
- TTL 到期后，再次请求应触发重新计算
- 游客推荐与登录用户推荐缓存不能串用
- 推荐页展示数据仍然完整（封面、作者、统计值、用户态）
- 不同分页请求应能复用同一个结果窗口，而不是重复构造多个 page key

### 行为验证
- 登录用户点赞 / 收藏某个视频后，短时间内推荐页可以暂时稳定
- TTL 到期后结果允许发生变化
- 推荐结果不是完全固定不变，而是短周期稳定、跨时间窗变化

### 日志验证
- 曝光日志仍然能正常记录 videoId 级别曝光
- 若采用 candidate 元信息缓存，需确认 score/channels 仍可落库

## 总结

这次推荐结果缓存的设计核心不是“把推荐固定住”，而是：

> 在一个很短的时间窗口内，避免重复执行昂贵的推荐计算，同时继续复用现有 `video:base:v2 + video:stat:{id}` 详情缓存体系。

因此首版最合理的方案是：

- 缓存推荐页分页后的 videoId 列表
- 游客和登录用户分 key
- 使用短 TTL
- 曝光日志继续保留
- 不缓存完整 `VideoVO`

这是当前项目阶段最稳、最容易落地、也最适合后续继续演进的推荐缓存实现路径。
