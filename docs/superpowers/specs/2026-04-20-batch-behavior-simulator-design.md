# Batch Behavior Simulator Design

## Goal

在 `backend/scripts/` 下提供一个本地测试脚本，用于批量模拟用户的播放、观看进度、点赞、收藏、关注行为，并在执行后输出推荐流和热榜结果摘要，替代手工逐个点击页面进行推荐/热门系统验证。

## Why this exists

当前项目已经能通过 seed 生成大量用户和视频数据，也已经具备推荐流、热榜、搜索和行为接口。但如果要继续验证推荐是否真的随用户兴趣变化、热门榜是否符合权重逻辑，靠手动点页面会有几个问题：

- 太慢，无法批量验证多个画像用户
- 不可重复，下一次很难构造完全相同的行为路径
- 很难控制行为分布（播放多少、点赞多少、关注多少）
- 无法快速回归推荐结果是否因行为变化而发生预期偏移

因此需要一个测试辅助脚本，用脚本化方式稳定、快速地注入行为，并输出推荐/热门结果，形成可复用的测试手段。

## Non-goals

本设计不打算：

- 新增后端业务接口专门用于模拟行为
- 修改现有推荐算法、热榜算法或搜索逻辑
- 引入浏览器自动化（如 Playwright/Selenium）
- 直接操作数据库写行为数据
- 为脚本提供复杂的图形界面
- 做成生产环境工具

## Existing project context

当前项目已经具备脚本所需的关键接口：

- 登录接口：`/user/login`（见 [UserController.java](backend/src/main/java/com/bilibili/video/controller/UserController.java#L43-L47)）
- 视频列表 / 推荐 / 热榜：`/video/list`、`/video/recommended`、`/video/hot`（见 [VideoController.java](backend/src/main/java/com/bilibili/video/controller/VideoController.java#L63-L125)）
- 播放与观看进度：`/video/{id}/play`、`/video/{id}/progress`（见 [VideoController.java](backend/src/main/java/com/bilibili/video/controller/VideoController.java#L210-L228)）
- 点赞：`/like/{videoId}`（见 [LikeController.java](backend/src/main/java/com/bilibili/video/controller/LikeController.java#L30-L50)）
- 收藏：`/favorite/{videoId}`（见 [FavoriteController.java](backend/src/main/java/com/bilibili/video/controller/FavoriteController.java#L20-L33)）
- 关注：`/follow/{userId}`（见 [FollowController.java](backend/src/main/java/com/bilibili/video/controller/FollowController.java#L21-L34)）
- 搜索：`/search`（见 [SearchController.java](backend/src/main/java/com/bilibili/video/controller/SearchController.java#L28-L38)）

这些接口足以支持一个完全独立于业务代码的测试脚本。

## User-approved constraints

- 脚本放在 `backend/scripts/`
- 当前阶段优先快速可用，而不是做成复杂配置系统
- 希望替代手动逐个点击页面的方式
- 目标是更高效地测试推荐系统和热门系统

## Alternatives considered

### Option A: 固定画像脚本

直接把少量画像和行为写死在脚本里，例如 technology / food / lifestyle / cold_start。

**Pros**
- 最快落地
- 最少输入
- 最适合快速验证

**Cons**
- 灵活性差
- 每次调整画像都要改代码

### Option B: 配置驱动脚本

脚本只负责执行，画像和行为强度由 JSON/YAML 配置提供。

**Pros**
- 灵活
- 可扩展
- 更适合长期做实验

**Cons**
- 初始复杂度高
- 当前需求下略重

### Option C: 半固定画像 + 命令行参数（recommended）

脚本内置若干画像模板，但允许通过命令行参数调用户数、行为强度、是否输出推荐/热门等。

**Pros**
- 比固定脚本灵活
- 比完整配置系统简单
- 很适合当前本地快速测试目的

**Cons**
- 后续如果画像很多，参数会逐渐增多

## Recommended approach

采用 **半固定画像 + 命令行参数** 的独立脚本方案，脚本放在 `backend/scripts/` 下。脚本本身不修改业务代码，不直接写数据库，只通过现有 HTTP API 模拟真实用户行为。

## High-level architecture

### Script location

脚本放在：

- `backend/scripts/simulate_behavior_profiles.py`

选择 Python 的原因：

- 对本地测试脚本足够轻量
- 发 HTTP 请求、处理 JSON、做参数解析都比较方便
- 不会污染后端主业务代码

### Inputs

脚本需要支持以下输入：

- `--base-url`：后端服务地址，默认 `http://localhost:8080`
- `--profile`：画像类型，支持内置模板
- `--users`：要模拟的账号数量
- `--plays`：每个用户播放次数
- `--likes`：每个用户点赞次数
- `--favorites`：每个用户收藏次数
- `--follows`：每个用户关注次数
- `--show-recommended`：是否输出推荐结果摘要
- `--show-hot`：是否输出热门结果摘要

脚本应有合理默认值，使用户只用最少参数就能跑起来。

### Profile templates

内置画像模板至少包括：

- `technology`
- `food`
- `lifestyle`
- `mixed`
- `cold_start`

每种画像模板定义：

- 优先搜索/筛选的关键词（如 `科技`、`美食`、`生活`）
- 优先分类名称
- 行为强度偏好
- 是否倾向关注作者
- 是否偏探索型行为

### Data source strategy

脚本不手写视频 ID，而是通过现有接口拉候选视频。

推荐的数据来源顺序：

1. `/search?keyword=<profile keyword>`
2. 如果搜索结果不足，回退到 `/video/list`
3. 如需测试热门系统，可读取 `/video/hot`

这样脚本能适配你当前数据库里的真实视频，而不是依赖写死 ID。

### Execution flow

对每个测试用户，脚本执行如下步骤：

1. 登录并获取 token
2. 根据画像模板拉取候选视频列表
3. 按画像挑选目标视频
4. 对目标视频依次调用：
   - `/video/{id}/play`
   - `/video/{id}/progress`
5. 再对一部分视频调用：
   - `/like/{videoId}`
   - `/favorite/{videoId}`
6. 从已交互视频里提取作者 ID，调用：
   - `/follow/{userId}`
7. 结束后，如果启用输出：
   - 调 `/video/recommended?page=1&size=10`
   - 调 `/video/hot?page=1&size=10`
8. 打印摘要

### Output

脚本应输出：

- 成功登录的测试账号
- 每个账号的实际行为计数：播放 / 点赞 / 收藏 / 关注
- 每个账号推荐结果前 10 条：
  - 标题
  - 作者
  - 分类
  - `isRecommended`
- 当前热门榜前 10 条：
  - 标题
  - 播放量
  - 点赞数
  - 收藏数

### Error handling

仅处理测试边界上必需的问题：

- 登录失败时明确报出账号名
- 搜索结果为空时回退到 `/video/list`
- 单次行为请求失败时记录并继续下一个目标，避免整轮中断
- 若完全没有可交互视频，则终止当前画像执行并报错

## Detailed behavior design

### Login strategy

脚本不负责创建用户，只使用已有测试账号。

默认方式：

- 命令行传入账号列表或账号前缀
- 脚本按这些账号登录

当前设计不把“自动注册测试账号”纳入首版范围，避免扩大脚本职责。

### Video selection rules

#### technology / food / lifestyle
- 优先通过 `/search` 拉与关键词直接相关的视频
- 如果结果不足，再从 `/video/list` 补齐
- 尽量避免一个用户对同一视频重复操作

#### mixed
- 同时从多个关键词拉候选，例如 `科技`、`生活`、`美食`
- 用较均匀的比例做行为注入

#### cold_start
- 不注入任何行为
- 只请求推荐流，观察推荐结果是否偏热门/新鲜/运营推荐

### Recommended output interpretation

脚本本身不做“推荐是否合理”的复杂评分，只做结构化输出。

原因：
- 推荐是否合理需要人结合业务意图判断
- 当前目标是先把人工检查成本从“点很多次页面”降到“看结构化摘要”

## Safety boundaries

- 脚本仅用于本地或测试环境
- 默认只操作你显式指定的测试账号
- 不自动删除任何数据
- 不改动数据库结构
- 不提供批量取消行为的功能作为首版能力

## Testing strategy

脚本本身的验证重点应是：

1. 能成功登录已有测试账号
2. 能从搜索或视频列表接口拿到候选视频
3. 能正确调用播放 / 点赞 / 收藏 / 关注接口
4. 能输出推荐和热门摘要
5. 在某个接口失败时不会导致整轮脚本全部崩溃

## Success criteria

这个脚本完成后，你应该能做到：

- 不再手动逐个点击页面做推荐测试
- 用一条命令模拟某种兴趣画像用户的行为
- 快速查看该画像用户的推荐结果前 10 条
- 快速查看当前热门榜前 10 条
- 重复执行同一组参数，稳定复现相近的测试路径

## Recommended next step

如果这份设计通过，下一步应写 implementation plan，明确：

- 脚本文件路径
- 参数解析方式
- 用户登录与 token 复用方式
- 候选视频拉取与筛选逻辑
- 行为请求发送顺序
- 输出格式与示例