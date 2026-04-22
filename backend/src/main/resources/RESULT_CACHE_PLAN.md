# 推荐结果与搜索结果缓存实施方案

## 1. 文档目的

这份文档用于统一说明项目下一步的 **结果层缓存（Result Cache）** 设计，覆盖两类场景：

1. 推荐结果缓存
2. 搜索结果缓存

当前项目已经具备：

- 视频详情内容层缓存：`video:base:v2:{id}`
- 视频统计层缓存：`video:stat:{id}`
- 搜索历史、热搜词、分类、标签等辅助缓存

但还缺少真正的：

- 推荐结果缓存
- 搜索结果缓存

因此本方案的核心目标是：

> 在不缓存完整 `VideoVO` 的前提下，缓存“短时间稳定、计算成本高、重复请求多”的结果层数据，并继续复用现有内容层缓存体系。

---

## 2. 总体设计原则

### 2.1 结果层只缓存 ID，不缓存完整 VO

结果层缓存只负责回答：

- 推荐页：这一轮推荐应该展示哪些视频，顺序如何
- 搜索页：这个 query 命中了哪些视频，总数多少

不直接缓存完整 `VideoVO`，原因是：

- `VideoVO` 含有聚合字段和用户态字段
- 失效复杂
- 与当前 `video:base:v2 + video:stat:{id}` 体系冲突

### 2.2 内容层与结果层分离

#### 内容层
- `video:base:v2:{id}`
- `video:stat:{id}`

#### 结果层
- 推荐结果窗口缓存
- 搜索结果缓存

这样两层职责清晰：

- 内容层负责“视频本身长什么样”
- 结果层负责“这一页应该展示哪些视频”

### 2.3 高计算链路优先缓存

优先缓存：
- 推荐结果
- 搜索结果
- 用户画像输入（后续可补）

不优先缓存：
- 更多静态元数据
- 完整 VO 结果页
- 全量用户点赞/收藏关系

### 2.4 曝光与日志必须基于最终返回结果

无论推荐结果是否命中缓存：

> 曝光日志必须基于“最终成功组装并返回给前端的视频集合”记录，不能直接基于缓存中的原始 `ids` 记录。

因为缓存中的原始结果在最终返回前，仍可能发生：

- 某视频不存在
- 某视频组装失败
- 某视频被过滤

---

## 3. 推荐结果缓存方案

## 3.1 缓存目标

推荐结果缓存的目标不是“让推荐永远固定”，而是：

- 在一个很短的时间窗口内避免重复跑完整推荐链路
- 允许结果短时间稳定
- 在 TTL 到期后自然变化
- 在用户强行为发生后可选做用户级失效

---

## 3.2 推荐缓存 key 设计

### 游客推荐

```text
rec:guest:home:v1:window:{windowSize}
```

例如：

```text
rec:guest:home:v1:window:50
```

### 登录用户推荐

```text
rec:user:{userId}:home:v1:window:{windowSize}
```

例如：

```text
rec:user:1001:home:v1:window:50
```

### 为什么用 `window` 而不是 `page`

因为推荐结果更像：

- 一组短时间稳定的有序结果窗口
- 再由前端请求按 `page/size` 切片

相比 `page key`，`window key` 的优点是：

- 更符合推荐系统结果语义
- 多页可以复用同一个窗口
- key 更稳定，不会碎成很多页级缓存
- 后续扩展 `scene`、`bucket`、`strategy version` 更自然

### 后续可扩展字段

如果后面要支持实验参数，可以扩成：

```text
rec:user:{userId}:home:v1:bucket:{bucketId}:window:50
```

但首版建议先只保留：

- `guest/user`
- `home`
- `v1`
- `windowSize`

---

## 3.3 推荐缓存 value 设计

建议 value 结构如下：

```json
{
  "ids": [101, 205, 309, 411],
  "windowSize": 50,
  "hasMore": true,
  "generatedAt": 1710000000,
  "meta": {
    "101": { "score": 8.23, "channels": ["hot", "tag"], "rank": 1 },
    "205": { "score": 7.82, "channels": ["fresh", "category"], "rank": 2 }
  }
}
```

### 字段说明

#### `ids`
推荐结果窗口中的有序 `videoId` 列表。

#### `windowSize`
本次缓存窗口长度，例如 50。

#### `hasMore`
是否还有下一页可供分页。

#### `generatedAt`
结果窗口生成时间戳。

作用：
- 排查这批推荐是什么时候生成的
- 为未来软过期预留接口
- 方便分析缓存窗口被复用了多久

#### `meta`
推荐缓存的附加元信息，建议使用 **map** 而不是数组。

原因：
- 按 `videoId` 取值更直接
- 分页切片后更容易关联
- 过滤某些视频时更好同步处理
- 曝光日志回填更顺手

### 为什么推荐缓存不保留 `total`
推荐场景中的 `total` 很容易产生歧义：

- 候选池总量？
- 本次重排总量？
- 窗口长度？
- 可分页结果总量？

因此首版不建议直接暴露一个语义模糊的 `total`，而是明确用：

- `windowSize`
- `hasMore`

如果后面真的形成明确的“推荐总量定义”，再考虑补 `total`。

---

## 3.4 推荐 TTL 策略

### 游客推荐
- 建议 TTL：**30 ~ 60 秒**
- 默认建议：**60 秒**
- 建议增加 10%~20% 抖动

### 登录用户推荐
- 建议 TTL：**10 ~ 20 秒**
- 默认建议：**15 秒**
- 同样建议增加 10%~20% 抖动

### 原因

游客推荐：
- 个性化弱
- 重复访问高
- 更适合削峰

登录用户推荐：
- 行为反馈更敏感
- 更需要短周期变化
- 但仍不需要每次请求都全量重算

---

## 3.5 推荐结果缓存重建保护

推荐 key（尤其游客 key）天然是热点 key，必须考虑缓存击穿问题。

### 首版最低配方案
增加一个短暂 rebuild lock：

- 只有一个请求负责回源重建推荐窗口
- 其他请求短暂等待后重试，或走兜底逻辑

### 后续可升级方案
- 软过期 + 异步刷新
- 后台预热热门窗口

### 当前建议

首版实现建议至少写入设计并实现：

> **推荐结果缓存需要具备单 key 重建保护能力，防止热点 key 同时击穿。**

---

## 3.6 推荐结果读写路径

### 未命中时

```text
请求推荐页
  -> RecommendationServiceImpl 执行完整推荐链路
  -> 得到短时间稳定的推荐结果窗口（有序 ids / 可选 meta）
  -> 写入推荐结果缓存
  -> 按当前 page/size 从窗口中切片
  -> 按切片结果组装 VideoVO
  -> 基于最终返回结果记录曝光日志
  -> 返回结果
```

### 命中时

```text
请求推荐页
  -> 查推荐结果窗口缓存
  -> 按当前 page/size 从窗口中切片
  -> 保序加载 Video
  -> 走现有内容层组装逻辑（base + stat + 用户态）
  -> 基于最终返回结果记录曝光日志
  -> 返回结果
```

---

## 3.7 推荐结果失效策略

### 首版策略
以 **TTL 为主**，不对每个行为做精细删缓存。

#### 游客推荐
- 主要靠 TTL 自然过期

#### 登录用户推荐
- 主要靠 TTL 自然过期
- 对强语义行为可选补充用户级失效，例如：
  - 点赞
  - 收藏
  - 评论
  - 完整观看

### 当前建议

为了控制复杂度，首版先：
- 主要依赖短 TTL
- 暂不在每个行为里显式删推荐缓存
- 只有效果不理想再补用户级失效

---

## 4. 搜索结果缓存方案

## 4.1 缓存目标

搜索结果缓存的目标不是缓存所有 query，而是：

- 缓存高频 query
- 降低 ES 重复查询
- 降低热门搜索结果的 DB 补查与组装成本
- 优先提升第一页体验

搜索场景与推荐不同：

- 推荐 key 空间可控
- 搜索 query 空间可能非常大

因此搜索缓存必须更强调：

> **值不值得缓存**

而不是单纯“能不能缓存”。

---

## 4.2 搜索缓存 key 设计

首版建议仍使用 **page key**：

```text
search:video:{normalizedKeyword}:sort:{sortBy}:page:{page}:size:{size}:v1
```

例如：

```text
search:video:java:sort:default:page:1:size:12:v1
```

### 为什么搜索不强行 window 化

因为搜索分页本身就是用户显式请求的一部分，page 级 key 语义天然明确：

- query
- sortBy
- page
- size

相比推荐结果，搜索场景没有必要为了风格统一而强行 window 化。

### 所有影响结果的参数都必须进 key

包括但不限于：
- `keyword`
- `sortBy`
- `page`
- `size`

后续如果再支持：
- `category`
- `duration`
- `scene`
- `order`

这些也必须进入 key。

---

## 4.3 query 规范化（必须做）

推荐缓存的 key 比较稳定，而搜索缓存最大的风险之一是 query 被不同形式重复表达，导致命中率被稀释。

### 建议最少做这些规范化

- `trim`
- `lowercase`
- 多空格压缩

### 后续可选增强

- 全角/半角统一
- 常见技术词轻量归一

### 目的

避免这些被当成不同 key：

- `Java`
- `java`
- ` java `
- `spring boot`
- `springboot`

搜索缓存必须使用 **规范化后的 query** 参与 key 构造。

---

## 4.4 搜索缓存准入控制（必须做）

首版不建议默认缓存所有 query。

### 建议首版只缓存满足这些条件的请求

- `page = 1`
- `size` 在常见范围内（如 10 / 12 / 20）
- `normalizedKeyword` 长度在合理范围内（如 2~20）
- 命中数较高，或属于近期高频 query

### 如果当前没有 query 热度统计

首版可以先更简单：

- 只缓存第一页
- 只缓存长度合理的 query
- 只缓存命中数较高的 query

这样做的好处是：
- 控制 key 空间
- 提高命中率
- 避免长尾 query 把 Redis 搞得太碎

---

## 4.5 搜索缓存 value 设计

建议结构：

```json
{
  "ids": [101, 205, 309],
  "total": 278,
  "generatedAt": 1710000000
}
```

### 字段说明

- `ids`：当前 query + sort + page + size 的命中 videoId 列表
- `total`：这个搜索结果的总命中数
- `generatedAt`：结果生成时间戳

### 空结果缓存

对于空结果，也建议缓存：

```json
{
  "ids": [],
  "total": 0,
  "generatedAt": 1710000000
}
```

### 空结果 TTL

建议比正常结果稍短，例如：
- `30 秒`

这样能减少热门误拼 query 的重复 ES 查询。

---

## 4.6 搜索 TTL 策略

### 正常结果
- 建议 TTL：**30 ~ 180 秒**
- 默认推荐：**60 秒**

### 空结果
- 建议 TTL：**30 秒**

### 原因

热门搜索结果往往比推荐更稳定，但搜索 key 空间更大，因此：
- TTL 可以稍长
- 但准入必须更严

---

## 4.7 搜索结果读写路径

### 命中时

```text
请求搜索
  -> 先规范化 query
  -> 判断是否满足缓存准入条件
  -> 查搜索结果缓存
  -> 按 ids 保序加载 Video
  -> 复用现有内容层组装逻辑
  -> 返回结果
```

### 未命中时

```text
请求搜索
  -> 先规范化 query
  -> 查 ES
  -> 查 DB / 组装 ids + total
  -> 若满足准入条件则写缓存
  -> 返回结果
```

---

## 5. 统一实施优先级

### 第一优先级
1. 推荐结果窗口缓存
2. 搜索 query 规范化
3. 搜索结果缓存准入控制

### 第二优先级
4. 推荐 rebuild lock
5. 搜索空结果缓存
6. 推荐 meta 与曝光日志联动

### 第三优先级
7. 推荐轻量打散
8. 游客按 bucket 分桶
9. 搜索 query 热度统计驱动准入

---

## 6. 最小改动范围

### 推荐缓存涉及文件
- `backend/src/main/java/com/bilibili/video/service/impl/RecommendationServiceImpl.java`
- `backend/src/main/java/com/bilibili/video/common/RedisConstants.java`
- 可复用：
  - `backend/src/main/java/com/bilibili/video/service/impl/VideoViewAssembler.java`
  - `backend/src/main/java/com/bilibili/video/service/impl/RecExposureLogServiceImpl.java`

### 搜索缓存涉及文件
- `backend/src/main/java/com/bilibili/video/service/impl/SearchServiceImpl.java`
- `backend/src/main/java/com/bilibili/video/common/RedisConstants.java`
- 可复用：
  - `backend/src/main/java/com/bilibili/video/service/impl/VideoViewAssembler.java`

---

## 7. 总结

最终方案可以概括为：

### 推荐结果缓存
- 使用 `window key`
- 缓存有序 `ids`
- value 带 `generatedAt`
- 需要 rebuild lock
- 曝光日志必须基于最终返回结果记录

### 搜索结果缓存
- 使用 `page key`
- key 基于规范化 query
- 必须有准入控制
- 缓存 `ids + total + generatedAt`
- 需要支持空结果缓存

### 与现有内容层缓存配合
最终所有结果层缓存都不直接缓存完整 `VideoVO`，而是继续复用：

- `video:base:v2:{id}`
- `video:stat:{id}`

这样整个缓存体系才会保持职责清晰、失效可控、后续可演进。
