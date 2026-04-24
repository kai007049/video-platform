# Refresh New Recommendation Batch Design

## Goal

在首页推荐页增加“换一批”能力：用户点击后，前后端协作尽量返回一批当前页未展示、且尽量没看过的视频，而不是简单重新拉取同一页数据。

## Why this exists

当前推荐页只是普通分页拉取：

- 前端在 [Home.vue](frontend/src/views/Home.vue) 中通过 `getRecommended(page, pageSize)` 获取第一页结果
- 后端推荐链路虽然已经有“最近观看惩罚”，但它只是降权，不保证点击刷新后会出现一批新的内容

这会导致：
- 用户点击“刷新”时，可能仍然看到和刚才高度重复的内容
- 对推荐体验和测试体验都不够友好

因此需要一个轻量、可控的“换一批”设计，让刷新更像产品能力，而不是普通重载。

## Non-goals

本设计不打算：

- 重写现有推荐算法
- 在热门页和最新页做同样的“换一批”能力
- 引入复杂探索算法、bandit 或强化学习策略
- 以“绝不重复内容”为目标
- 修改数据库结构

## Existing context

### Frontend
首页当前数据来源：
- 推荐：`getRecommended(page, pageSize)`
- 热门：`getHotList(page, pageSize)`
- 最新：`getVideoList(page, pageSize)`

见 [Home.vue](frontend/src/views/Home.vue#L450-L467)

### Backend
推荐接口当前走：
- `/video/recommended`
- `VideoService.listRecommended(...)`
- `RecommendationServiceImpl.listRecommended(...)`

当前推荐内部已经有：
- 热门召回
- 新鲜召回
- 标签兴趣召回
- 分类偏好召回
- 作者偏好召回
- 运营推荐召回
- 最近观看惩罚

但没有“排除当前页已展示视频 ID”这一输入。

## User-approved direction

采用前后端混合方案。

## Alternatives considered

### Option A: 纯前端去重刷新
前端记录当前页已展示视频 ID，重新请求第一页，然后本地过滤重复项。

**Pros**
- 实现快
- 不改后端

**Cons**
- 只能在当前前端结果上做局部过滤
- 如果第一页候选本来就窄，过滤后可能不够用
- 不知道用户真实“最近已看”数据

### Option B: 纯后端 refresh 模式
后端新增 `refresh=true`，只靠服务端历史和惩罚逻辑决定返回另一批内容。

**Pros**
- 语义统一
- 更贴近真实推荐能力

**Cons**
- 改动较重
- 规则不透明，前端很难控制当前页重复问题

### Option C: 前后端混合（recommended）
前端传当前页已展示视频 ID 给后端，后端在推荐候选 / 重排阶段优先排除这些 ID；如果排除后不足，再用剩余候选补齐。

**Pros**
- 改动可控
- 不需要推翻现有推荐算法
- 比纯前端过滤更稳
- 更符合“换一批”的直觉

**Cons**
- 需要新增前后端接口契约

## Recommended approach

仅在 **推荐页** 增加“换一批”能力，不扩散到热门页和最新页。

### Frontend responsibility

前端负责：
- 维护当前推荐页已展示的视频 ID 集合
- 点击“换一批”时，把这些 ID 作为 `excludeVideoIds` 传给后端
- 用新的返回结果替换当前推荐列表
- 同时把本轮新展示的视频继续加入已展示集合

### Backend responsibility

后端负责：
- 在推荐接口增加可选参数 `excludeVideoIds`
- 在推荐候选结果中优先过滤这些 ID
- 如果过滤后不足一页，允许用未排除的剩余结果补齐，避免返回数量过少
- 保持当前推荐算法主体不变，只在结果裁剪阶段做轻量过滤

## API design

### Request

现有推荐接口：
- `GET /video/recommended?page=1&size=12`

扩展为：
- `GET /video/recommended?page=1&size=12&excludeVideoIds=1,2,3`

其中：
- `excludeVideoIds` 可选
- 仅在推荐页“换一批”时传
- 普通首次加载不传

### Backend parsing

后端把 `excludeVideoIds` 解析成 `Set<Long>`。

解析策略：
- 空值 → 空集合
- 非法 ID → 忽略单项，不影响整体
- 数量过大时可截断，例如最多取前 200 个，避免请求过长

## Backend implementation design

### Controller layer

在推荐接口控制器层新增可选参数：
- `excludeVideoIds`

### Service layer

`VideoService.listRecommended(...)` 与 `RecommendationService.listRecommended(...)` 透传该集合。

### Recommendation layer

推荐核心不重做，只在“最终窗口输出”阶段应用排除逻辑：

1. 先按当前已有逻辑完成召回、打分、重排
2. 得到排序后的候选列表
3. 去掉 `excludeVideoIds` 中的视频
4. 如果过滤后数量不足 `size` 或窗口要求，则允许从未过滤的原始排序候选中继续补足
5. 返回最终一页结果

这样可保证：
- 主排序逻辑不被破坏
- “换一批”优先生效
- 不会因过滤过严导致前端拿到很少结果

## Frontend implementation design

### State

首页推荐页需要新增：
- `seenRecommendedIds: Set<number>` 或等价数组状态
- 仅在推荐 tab 使用

### First load

- 初次进入推荐页时正常拉第一页
- 把返回结果的 ID 记录进 `seenRecommendedIds`

### Refresh button

推荐页新增按钮：
- 文案：`换一批`

点击后：
1. 带当前 `seenRecommendedIds` 请求推荐接口
2. 用新结果替换当前推荐列表
3. 把新结果 ID 追加进 `seenRecommendedIds`

### Tab behavior

- 切到热门 / 最新时，不显示 `换一批`
- 切回推荐时继续复用已有 `seenRecommendedIds`
- 如果用户手动刷新页面，`seenRecommendedIds` 可清空，重新开始一轮推荐浏览

当前设计不要求跨页面持久化这个集合。

## Data flow

```text
用户点击推荐页“换一批”
  ->
前端收集当前已展示推荐视频 ID
  ->
请求 /video/recommended?excludeVideoIds=...
  ->
后端按现有推荐逻辑完成召回/打分/重排
  ->
过滤 excludeVideoIds
  ->
补齐结果
  ->
返回新一批推荐视频
  ->
前端替换当前推荐列表并扩展 seenRecommendedIds
```

## Error handling

- 如果“换一批”请求失败：
  - 保持当前列表不变
  - 给出轻量错误提示
- 如果过滤后仍返回和当前高度重复：
  - 允许发生，不做过度保证
- 如果 `excludeVideoIds` 超长：
  - 后端截断
  - 不报错

## Testing strategy

### Backend
- 测试 `excludeVideoIds` 能正确解析
- 测试推荐结果会优先排除传入 ID
- 测试排除后数量不足时仍能补齐
- 测试不传 `excludeVideoIds` 时行为与当前一致

### Frontend
- 测试推荐页首次加载会记录已展示 ID
- 测试点击“换一批”会携带 `excludeVideoIds`
- 测试推荐页结果会被替换，而不是追加
- 测试热门/最新页不显示“换一批”

## Success criteria

实现完成后：
- 推荐页出现“换一批”按钮
- 点击后通常能看到一批和当前页明显不同的内容
- 热门页 / 最新页不受影响
- 不需要重写推荐算法
- 代码改动集中、边界清晰

## Recommended next step

如果这份设计通过，下一步应写 implementation plan，明确：
- 前端状态字段、按钮位置和交互
- 后端 controller/service/recommendation 透传链路
- 过滤与补齐逻辑的具体落点
- 前后端测试与验证命令