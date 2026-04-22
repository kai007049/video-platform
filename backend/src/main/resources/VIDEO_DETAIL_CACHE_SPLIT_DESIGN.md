# Video-Platform 视频详情缓存拆分设计

## 1. 文档目的

这份文档用于明确 **视频详情缓存从“整对象缓存”向“base + stats 分层缓存”演进** 的设计方案，目标是先把字段边界、Redis key 设计、读写路径和失效策略讲清楚，再决定是否正式实施代码重构。

这份设计重点回答四个问题：

1. 视频详情缓存为什么要拆分？
2. 哪些字段应该留在 `base`，哪些应该拆到 `stats`？
3. `stats` 应该拆成多个 Redis key，还是一个 Redis Hash？
4. 拆分后读写路径和失效逻辑应该如何设计？

---

## 2. 当前问题

当前项目的视频详情缓存以 `VideoVO` 聚合对象为中心，虽然读路径简单，但存在以下问题：

### 2.1 缓存粒度过粗

一个完整 `VideoVO` 中混合了：

- 低频变化字段：标题、简介、封面、作者、分类等
- 高频变化字段：播放数、点赞数、收藏数
- 部分用户态字段：点赞状态、收藏状态、观看进度（由组装逻辑补充）

这会导致：

- 播放一次视频就要失效整个详情缓存
- 点赞/收藏一次也要失效整个详情缓存
- 高频更新字段拖着低频稳定字段一起反复失效

### 2.2 写路径已经部分 stats 化，但读路径还没正式收敛

项目当前其实已经在 Redis 中维护统计值增量：

- 播放：`VideoCommandService.recordPlayCount()`
- 点赞：`LikeServiceImpl.like()/unlike()`
- 收藏：`FavoriteServiceImpl.add()/remove()`

这些地方已经在使用：

```text
video:stat:{videoId}
```

以及字段：

- `play`
- `like`
- `save`

也就是说，写路径已经部分走向 stats 原子计数，但当前缓存方案在概念上仍然是“整详情对象缓存”，导致设计不够统一。

---

## 3. 设计目标

这次缓存拆分设计的目标是：

1. 让高频变化统计值从详情主对象中独立出来。
2. 保持读路径仍然足够简单，不引入大量 Redis 往返。
3. 复用项目中已经存在的 Redis stats 设计，而不是重新发明一套计数方案。
4. 让未来多实例下的缓存失效与热点本地缓存策略更容易继续演进。

---

## 4. 推荐的缓存分层方案

推荐将视频详情拆成两层：

```text
video:{id}:base
video:stat:{id}
```

其中：

- `base`：低频变化、稳定展示字段
- `stats`：高频变化统计字段

这是当前阶段最合理的收敛方式。

---

## 5. base 层字段设计

### key

```text
video:{id}:base
```

### 建议包含字段

- `id`
- `title`
- `description`
- `authorId`
- `coverUrl`
- `previewUrl`
- `videoUrl`
- `durationSeconds`
- `isRecommended`
- `categoryId`
- `createTime`

### 为什么这些字段留在 base

这些字段有共同特点：

- 写入频率低
- 值稳定
- 主要由视频上传、编辑、封面处理、后台推荐位等操作触发变更
- 不需要高频原子增减

这些字段非常适合作为一个相对稳定的缓存对象整体读取。

---

## 6. stats 层字段设计

### key

```text
video:stat:{id}
```

### 结构

使用 **Redis Hash** 存储，而不是拆成多个独立 key。

### fields

- `play`
- `like`
- `save`

### 为什么用一个 Hash，而不是 3 个独立 key

#### 方案 A：3 个独立 key

例如：

```text
video:stat:{id}:play
video:stat:{id}:like
video:stat:{id}:save
```

虽然也能用 `INCRBY`，但问题是：

- key 数量增多
- TTL 要维护 3 份
- 删除/清理要删 3 个 key
- 这 3 个值通常总是一起读取，拆散后收益不明显

#### 方案 B：一个 stats 整体对象，每次整包读写

例如：

```json
{"play": 10, "like": 2, "save": 1}
```

问题是：

- 每次加减要先读后写
- 原子性差
- 与当前已存在的 `opsForHash().increment(...)` 模式不一致

#### 推荐方案 C：一个 Hash + field 级原子更新

例如：

```text
HINCRBY video:stat:{id} play 1
HINCRBY video:stat:{id} like 1
HINCRBY video:stat:{id} save 1
```

读取时：

```text
HMGET video:stat:{id} play like save
```

这是当前项目里最合理的方案，因为：

- 一个 key 承载同一业务对象的统计字段，语义清晰
- 支持字段级原子增减
- 读路径可以一次性批量读取
- 与当前代码已有实现一致

---

## 7. 为什么暂时不把 commentCount 放进 stats

`commentCount` 从业务语义上看也像统计值，但当前项目不建议在这次重构里一起放进去。

### 原因

当前评论实现已经明确采用：

> 评论数以评论表聚合结果为准，不额外维护 Redis comment delta，避免统计口径分裂。

这意味着如果现在强行把 `commentCount` 也塞进 `video:stat:{id}`，会带来额外问题：

- 评论新增、删除、回复等场景一致性更复杂
- Redis commentCount 与数据库实际评论数容易偏离
- 需要额外的更新规范和修正机制

### 当前建议

这次先只把以下统计值纳入 stats：

- `play`
- `like`
- `save`

而 `commentCount` 继续保持当前策略，后续如果评论链路要进一步优化，再单独设计。

---

## 8. 读路径设计

拆分后的视频详情读取流程建议如下：

```text
1. 读取 video:{id}:base
2. 读取 video:stat:{id} 的 play/like/save
3. 补充 commentCount（沿用当前口径）
4. 如用户已登录，再补 liked/favorited/lastWatchSeconds
5. 组装最终 VideoVO
```

### 为什么这样不会明显拖慢性能

关键不在于“拆分了几个对象”，而在于：

- `base` 是一次读取
- `stats` 是一次 `HMGET`
- 而不是把每个字段拆成一个独立 key 再逐个读

所以这不是“拆成很多次 Redis 请求”，而是：

- 1 次读 base
- 1 次读 stats
- 1 次（可选）补用户态

这在工程上是可以接受的。

---

## 9. 写路径设计

### 9.1 base 的写路径

这些操作应影响 `base`：

- 上传视频
- 更新视频信息
- 封面变化
- 后台修改推荐位
- 删除视频

处理原则：

- 优先删 base 缓存
- 下次访问时重建

### 9.2 stats 的写路径

这些操作应直接更新 `video:stat:{id}`：

- 播放：`play + 1`
- 点赞：`like + 1`
- 取消点赞：`like - 1`
- 收藏：`save + 1`
- 取消收藏：`save - 1`

处理原则：

- 使用 Redis Hash 字段原子更新
- 不因为统计变化就删整个 base 缓存

---

## 10. 用户态缓存是否一起拆

当前不建议和这次 `base + stats` 重构一起做。

### 当前用户态包括

- `liked`
- `favorited`
- `lastWatchSeconds`

### 为什么暂时不一起拆

因为用户态属于：

- 用户维度
- 非共享缓存
- key 数量更大
- 命中模型和 base/stats 不一样

如果这次一起上，会让重构范围扩大很多。

### 当前建议

- `base + stats` 先做好
- 用户态继续沿用现有补充方式
- 后续如果需要，再单独设计：

```text
video:userstate:{userId}:{videoId}
```

---

## 11. category 和 author 是否要拆成单独缓存

### category

当前不建议新增 `category:{id}` 单条缓存。

原因：

- 项目已经有 `category:list` 和 `category:tree` 缓存
- 分类数量不大
- 直接从现有分类缓存里做映射即可

### author

`user:profile:{authorId}` 可以作为后续可选优化，但不是这次 `base + stats` 重构必须项。

原因：

- 这次核心目标是解决“详情对象统计字段粒度太粗”
- 如果同时再拆作者信息，会扩大改动面

### 当前建议

本次先不动：
- category 单 key 缓存
- author profile 单 key 缓存

---

## 12. 推荐的阶段性实施顺序

### 第一阶段（推荐当前就做）

先完成：

- `video:base:{id}`
- `video:stat:{id}`
- 读路径按 base + stats 组装

### 第二阶段（可选）

再考虑：

- `user:profile:{authorId}`

### 第三阶段（后续再说）

最后再考虑：

- `video:userstate:{userId}:{videoId}`
- `commentCount` 进一步收口进 stats 或其他独立统计层

---

## 13. 方案总结

当前项目里，视频详情缓存最合理的拆法不是“每个字段一个 key”，也不是继续把整个 `VideoVO` 完整揉成一个缓存对象，而是：

```text
video:base:{id}
video:stat:{id}
```

其中：

- `base` 保存低频、稳定、业务语义明确的详情字段
- `stats` 使用一个 Redis Hash key，内部通过 `play/like/save` 三个 field 做原子更新

### 最终推荐

#### base
包含：
- id
- title
- description
- authorId
- coverUrl
- previewUrl
- videoUrl
- durationSeconds
- isRecommended
- categoryId
- createTime

#### stats
使用：

```text
key: video:stat:{id}
fields: play, like, save
```

操作方式：
- `HINCRBY`
- `HMGET`

### 最重要的结论

> `stats` 不建议拆成 3 个独立 Redis key；也不建议整包序列化后反复回写。
>
> 最适合当前项目的方式是：**一个 stats Hash key + 多个 field + 字段级原子操作。**
