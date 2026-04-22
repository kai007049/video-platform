# Behavior Simulator Script

## Purpose

批量模拟推荐测试用户的播放、点赞、收藏、关注行为，并在需要时输出推荐流与热榜摘要。

这个脚本的目标不是替代业务代码，而是：

- 用现有 HTTP API 构造用户行为
- 快速验证推荐是否会随画像发生偏移
- 快速观察热门榜在多账号行为下的变化
- 替代手工逐个点击页面做回归验证

## Example

### Account login mode

```bash
python backend/scripts/simulate_behavior_profiles.py \
  --base-url http://localhost:8080 \
  --profile technology \
  --accounts tech1:password:captchaKey:captchaValue \
  --show-recommended \
  --show-hot
```

### Token mode

```bash
python backend/scripts/simulate_behavior_profiles.py \
  --base-url http://localhost:8080 \
  --profile technology \
  --tokens "$TOKEN_1" "$TOKEN_2" \
  --show-recommended \
  --show-hot
```

## Built-in profiles

- `technology`
- `food`
- `lifestyle`
- `mixed`
- `cold_start`

这些画像模板当前主要影响：
- 候选视频关键词
- 播放 / 点赞 / 收藏 / 关注数量
- 是否执行 browse / interact 阶段

## Input rules

- `--accounts` 与 `--tokens` 必须二选一
- 两者都传会直接报错
- 两者都不传也会直接报错
- `--users` 如果未传，默认等于所选输入数量
- `--users` 一旦传入，必须与 `--accounts` 或 `--tokens` 的条目数严格一致
- `--tokens` 模式不会调用 `/user/login`，而是直接用 token 请求 `GET /user/info` 获取真实用户名后进入后续行为模拟

## What it does

当前脚本会按以下顺序执行：

1. 登录账号，或使用现成 token 构造会话
2. 根据画像关键词通过 `/search` 拉候选视频
3. 如果搜索结果不足，回退到 `/video/list`
4. 对选中的视频模拟：
   - `/video/{id}/play`
   - `/video/{id}/progress`
   - `/like/{id}`
   - `/favorite/{id}`
5. 从已播放视频里提取作者，模拟 `/follow/{authorId}`
6. （可选）输出推荐流摘要和热门榜摘要

## Summary output

- `--show-recommended`：在每个账号完成行为模拟后，分别打印该账号推荐流前 10 条摘要
- `--show-hot`：在整个批次全部账号执行完成后，统一打印一次热榜前 10 条摘要，不会因账号数量重复输出

输出内容重点包括：
- 视频标题
- 作者名称
- 分类 ID
- `isRecommended`
- 热门视频的播放/点赞/收藏计数

## Practical usage suggestions

### 验证冷启动
```bash
python backend/scripts/simulate_behavior_profiles.py \
  --base-url http://localhost:8080 \
  --profile cold_start \
  --tokens "$TOKEN_1" \
  --show-recommended \
  --show-hot
```

### 验证美食画像
```bash
python backend/scripts/simulate_behavior_profiles.py \
  --base-url http://localhost:8080 \
  --profile food \
  --tokens "$TOKEN_1" \
  --show-recommended \
  --show-hot
```

### 验证科技画像
```bash
python backend/scripts/simulate_behavior_profiles.py \
  --base-url http://localhost:8080 \
  --profile technology \
  --tokens "$TOKEN_1" \
  --show-recommended \
  --show-hot
```

## Current boundaries

当前脚本已经能用于本地推荐 / 热门验证，但也有边界：

- 不自动注册用户
- 不自动获取验证码
- 不直接写数据库
- 不做复杂统计分析，只输出结构化摘要
- 主要适合本地或测试环境，不适合生产环境

## Why it matters

这个脚本最大的价值在于：

> 它让推荐系统从“接口存在”变成“行为可验证”，能快速比较 cold_start、technology、food 等不同画像下推荐结果是否合理。