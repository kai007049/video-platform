# Batch Behavior Simulator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `backend/scripts/` 下实现一个可重复运行的本地测试脚本，批量模拟播放、进度、点赞、收藏、关注行为，并输出推荐流与热榜摘要，替代手工点击页面做推荐/热门验证。

**Architecture:** 采用独立 Python 脚本方案，不修改后端业务代码。脚本通过现有登录、视频、搜索、点赞、收藏、关注接口完成真实行为注入，内置少量画像模板并支持命令行参数覆盖，最后输出推荐/热榜摘要。

**Tech Stack:** Python 3, standard library (`argparse`, `json`, `urllib`), backend HTTP APIs, JUnit-free smoke verification via direct script execution

---

## File Structure

### New files
- `backend/scripts/simulate_behavior_profiles.py` — 主脚本，负责参数解析、登录、候选视频拉取、行为模拟、结果输出
- `backend/scripts/README-behavior-simulator.md` — 仅针对该脚本的简短使用说明，包含示例命令和画像模板说明

### Modified files
- `backend/README.md` — 增加脚本入口和使用方式

### Validation artifacts
- 无新增测试框架文件；首版通过脚本级 smoke run 验证

---

### Task 1: 搭建脚本 CLI 骨架与画像模板

**Files:**
- Create: `backend/scripts/simulate_behavior_profiles.py`
- Create: `backend/scripts/README-behavior-simulator.md`

- [ ] **Step 1: 先写一个最小失败脚本，固定退出并打印 usage 占位**

```python
#!/usr/bin/env python3
import sys

if __name__ == "__main__":
    print("simulate_behavior_profiles.py: not implemented", file=sys.stderr)
    sys.exit(1)
```

- [ ] **Step 2: 运行脚本，确认当前是失败状态**

Run:

```bash
python backend/scripts/simulate_behavior_profiles.py
```

Expected:
- exit code 非 0
- stderr 包含 `not implemented`

- [ ] **Step 3: 写最小实现，支持 CLI 参数和内置画像模板**

将文件替换为以下骨架：

```python
#!/usr/bin/env python3
from __future__ import annotations

import argparse
from dataclasses import dataclass
from typing import Dict, List


@dataclass(frozen=True)
class ProfileTemplate:
    name: str
    keywords: List[str]
    plays: int
    likes: int
    favorites: int
    follows: int


PROFILE_TEMPLATES: Dict[str, ProfileTemplate] = {
    "technology": ProfileTemplate("technology", ["科技", "编程开发"], 20, 8, 3, 2),
    "food": ProfileTemplate("food", ["美食", "探店"], 20, 8, 3, 2),
    "lifestyle": ProfileTemplate("lifestyle", ["生活", "Vlog"], 20, 8, 3, 2),
    "mixed": ProfileTemplate("mixed", ["科技", "生活", "美食"], 18, 6, 2, 2),
    "cold_start": ProfileTemplate("cold_start", [], 0, 0, 0, 0),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Batch behavior simulator for recommendation testing")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--profile", choices=PROFILE_TEMPLATES.keys(), required=True)
    parser.add_argument("--users", type=int, default=1)
    parser.add_argument("--plays", type=int)
    parser.add_argument("--likes", type=int)
    parser.add_argument("--favorites", type=int)
    parser.add_argument("--follows", type=int)
    parser.add_argument("--show-recommended", action="store_true")
    parser.add_argument("--show-hot", action="store_true")
    parser.add_argument("--accounts", nargs="+", required=True,
                        help="Repeated account specs: username:password:captchaKey:captchaValue")
    return parser.parse_args()


def resolve_profile(args: argparse.Namespace) -> ProfileTemplate:
    base = PROFILE_TEMPLATES[args.profile]
    return ProfileTemplate(
        name=base.name,
        keywords=base.keywords,
        plays=base.plays if args.plays is None else args.plays,
        likes=base.likes if args.likes is None else args.likes,
        favorites=base.favorites if args.favorites is None else args.favorites,
        follows=base.follows if args.follows is None else args.follows,
    )


def main() -> int:
    args = parse_args()
    profile = resolve_profile(args)
    print(f"profile={profile.name} users={args.users} plays={profile.plays} likes={profile.likes} favorites={profile.favorites} follows={profile.follows}")
    print(f"accounts={len(args.accounts)} base_url={args.base_url}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 4: 运行脚本确认 CLI 正常工作**

Run:

```bash
python backend/scripts/simulate_behavior_profiles.py --profile technology --accounts demo:pass:key:code
```

Expected:
- exit code = 0
- stdout 包含 `profile=technology`
- stdout 包含 `accounts=1`

- [ ] **Step 5: 写脚本 README**

`backend/scripts/README-behavior-simulator.md`

```md
# Behavior Simulator Script

## Purpose

批量模拟推荐测试用户的播放、点赞、收藏、关注行为。

## Example

```bash
python backend/scripts/simulate_behavior_profiles.py \
  --base-url http://localhost:8080 \
  --profile technology \
  --accounts tech1:password:captchaKey:captchaValue \
  --show-recommended \
  --show-hot
```

## Built-in profiles

- technology
- food
- lifestyle
- mixed
- cold_start
```

- [ ] **Step 6: Commit**

```bash
git add backend/scripts/simulate_behavior_profiles.py backend/scripts/README-behavior-simulator.md

git commit -m "feat: add behavior simulator script skeleton"
```

### Task 2: 实现登录与 API 客户端

**Files:**
- Modify: `backend/scripts/simulate_behavior_profiles.py`

- [ ] **Step 1: 写一个失败 smoke path，要求登录返回 token**

在脚本中新增这些结构（先只加接口，不实现 HTTP）：

```python
@dataclass(frozen=True)
class AccountSpec:
    username: str
    password: str
    captcha_key: str
    captcha_value: str


def parse_account(raw: str) -> AccountSpec:
    username, password, captcha_key, captcha_value = raw.split(":", 3)
    return AccountSpec(username, password, captcha_key, captcha_value)


class ApiClient:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")

    def login(self, account: AccountSpec) -> str:
        raise NotImplementedError("login not implemented")
```

并在 `main()` 中尝试调用一次 `client.login(...)`。

- [ ] **Step 2: 运行脚本，确认登录路径先失败**

Run:

```bash
python backend/scripts/simulate_behavior_profiles.py --profile technology --accounts tech1:pass:key:code
```

Expected:
- 抛出 `NotImplementedError: login not implemented`

- [ ] **Step 3: 写最小 HTTP 客户端实现**

把下面代码补进脚本：

```python
import json
import urllib.request
import urllib.error


def _json_request(method: str, url: str, payload=None, token: str | None = None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read().decode("utf-8"))


class ApiClient:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")

    def login(self, account: AccountSpec) -> str:
        response = _json_request("POST", f"{self.base_url}/user/login", {
            "account": account.username,
            "password": account.password,
            "captchaKey": account.captcha_key,
            "captchaValue": account.captcha_value,
        })
        if response.get("code") != 200:
            raise RuntimeError(f"login failed for {account.username}: {response}")
        token = (((response.get("data") or {}).get("token")))
        if not token:
            raise RuntimeError(f"missing token for {account.username}: {response}")
        return token
```

- [ ] **Step 4: 运行登录 smoke test**

Run（换成你本地真实测试账号）:

```bash
python backend/scripts/simulate_behavior_profiles.py --profile cold_start --accounts tech1:你的密码:captchaKey:captchaValue
```

Expected:
- 能拿到 token
- 脚本不再报 `login not implemented`
- 如果验证码无效，能看到明确错误信息

- [ ] **Step 5: Commit**

```bash
git add backend/scripts/simulate_behavior_profiles.py

git commit -m "feat: add behavior simulator login client"
```

### Task 3: 拉取候选视频并执行行为

**Files:**
- Modify: `backend/scripts/simulate_behavior_profiles.py`

- [ ] **Step 1: 写一个失败路径，要求脚本能获取候选视频**

先加入待实现接口：

```python
class ApiClient:
    ...
    def search_videos(self, keyword: str) -> list[dict]:
        raise NotImplementedError("search not implemented")

    def list_videos(self) -> list[dict]:
        raise NotImplementedError("list not implemented")

    def record_play(self, video_id: int, token: str) -> None:
        raise NotImplementedError("play not implemented")

    def save_progress(self, video_id: int, seconds: int, token: str) -> None:
        raise NotImplementedError("progress not implemented")

    def like(self, video_id: int, token: str) -> None:
        raise NotImplementedError("like not implemented")

    def favorite(self, video_id: int, token: str) -> None:
        raise NotImplementedError("favorite not implemented")

    def follow(self, user_id: int, token: str) -> None:
        raise NotImplementedError("follow not implemented")
```

在 `main()` 中调用 `search_videos(...)`，验证先红。

- [ ] **Step 2: 运行脚本，确认候选视频路径先失败**

Run:

```bash
python backend/scripts/simulate_behavior_profiles.py --profile technology --accounts tech1:pass:key:code
```

Expected:
- 抛出 `NotImplementedError: search not implemented`

- [ ] **Step 3: 写最小实现，打通搜索/列表/行为接口**

补充这些方法：

```python
class ApiClient:
    ...
    def search_videos(self, keyword: str) -> list[dict]:
        response = _json_request("GET", f"{self.base_url}/search?keyword={urllib.parse.quote(keyword)}&page=1&size=30&sortBy=comprehensive")
        if response.get("code") != 200:
            raise RuntimeError(f"search failed: {response}")
        return (((response.get("data") or {}).get("records")) or [])

    def list_videos(self) -> list[dict]:
        response = _json_request("GET", f"{self.base_url}/video/list?page=1&size=30")
        if response.get("code") != 200:
            raise RuntimeError(f"list videos failed: {response}")
        return (((response.get("data") or {}).get("records")) or [])

    def record_play(self, video_id: int, token: str) -> None:
        _json_request("POST", f"{self.base_url}/video/{video_id}/play", token=token)

    def save_progress(self, video_id: int, seconds: int, token: str) -> None:
        _json_request("POST", f"{self.base_url}/video/{video_id}/progress?seconds={seconds}", token=token)

    def like(self, video_id: int, token: str) -> None:
        _json_request("POST", f"{self.base_url}/like/{video_id}", token=token)

    def favorite(self, video_id: int, token: str) -> None:
        _json_request("POST", f"{self.base_url}/favorite/{video_id}", token=token)

    def follow(self, user_id: int, token: str) -> None:
        _json_request("POST", f"{self.base_url}/follow/{user_id}", token=token)
```

同时补上候选挑选逻辑：

```python
def fetch_candidate_videos(client: ApiClient, profile: ProfileTemplate) -> list[dict]:
    candidates: list[dict] = []
    for keyword in profile.keywords:
        candidates.extend(client.search_videos(keyword))
    if not candidates:
        candidates.extend(client.list_videos())
    dedup: dict[int, dict] = {}
    for item in candidates:
        video_id = item.get("id")
        if isinstance(video_id, int) and video_id not in dedup:
            dedup[video_id] = item
    return list(dedup.values())
```

- [ ] **Step 4: 加入最小执行循环**

在 `main()` 中对每个账号执行：

```python
def simulate_user(client: ApiClient, token: str, profile: ProfileTemplate, candidates: list[dict]) -> dict:
    chosen = candidates[: max(profile.plays, profile.likes, profile.favorites, profile.follows, 1)]
    acted_authors: list[int] = []

    for item in chosen[:profile.plays]:
        video_id = item["id"]
        client.record_play(video_id, token)
        client.save_progress(video_id, 60, token)
        author_id = item.get("authorId")
        if isinstance(author_id, int):
            acted_authors.append(author_id)

    for item in chosen[:profile.likes]:
        client.like(item["id"], token)

    for item in chosen[:profile.favorites]:
        client.favorite(item["id"], token)

    followed = 0
    for author_id in acted_authors:
        if followed >= profile.follows:
            break
        client.follow(author_id, token)
        followed += 1

    return {
        "plays": min(profile.plays, len(chosen)),
        "likes": min(profile.likes, len(chosen)),
        "favorites": min(profile.favorites, len(chosen)),
        "follows": followed,
    }
```

- [ ] **Step 5: 做一次脚本 smoke run**

Run（换成真实测试账号）:

```bash
python backend/scripts/simulate_behavior_profiles.py --profile technology --accounts tech1:你的密码:captchaKey:captchaValue
```

Expected:
- 脚本能完成一轮播放/点赞/收藏/关注
- 后端接口返回 200
- 没有崩溃

- [ ] **Step 6: Commit**

```bash
git add backend/scripts/simulate_behavior_profiles.py

git commit -m "feat: simulate recommendation behaviors via api"
```

### Task 4: 输出推荐流与热门榜摘要，并补 README

**Files:**
- Modify: `backend/scripts/simulate_behavior_profiles.py`
- Modify: `backend/scripts/README-behavior-simulator.md`
- Modify: `backend/README.md`

- [ ] **Step 1: 写一个失败路径，要求支持输出推荐流和热门榜摘要**

先加待实现接口：

```python
class ApiClient:
    ...
    def recommended(self, token: str) -> list[dict]:
        raise NotImplementedError("recommended not implemented")

    def hot(self) -> list[dict]:
        raise NotImplementedError("hot not implemented")
```

并在 `main()` 中，如果带 `--show-recommended` 或 `--show-hot`，调用它们，先确认失败。

- [ ] **Step 2: 运行脚本，确认摘要输出路径先失败**

Run:

```bash
python backend/scripts/simulate_behavior_profiles.py --profile technology --accounts tech1:pass:key:code --show-recommended --show-hot
```

Expected:
- 抛出 `NotImplementedError: recommended not implemented` 或 `hot not implemented`

- [ ] **Step 3: 写最小实现，拉取推荐与热门摘要**

补充：

```python
class ApiClient:
    ...
    def recommended(self, token: str) -> list[dict]:
        response = _json_request("GET", f"{self.base_url}/video/recommended?page=1&size=10", token=token)
        if response.get("code") != 200:
            raise RuntimeError(f"recommended failed: {response}")
        return (((response.get("data") or {}).get("records")) or [])

    def hot(self) -> list[dict]:
        response = _json_request("GET", f"{self.base_url}/video/hot?page=1&size=10")
        if response.get("code") != 200:
            raise RuntimeError(f"hot failed: {response}")
        return (((response.get("data") or {}).get("records")) or [])


def print_video_summary(title: str, videos: list[dict]) -> None:
    print(f"\n=== {title} ===")
    for idx, item in enumerate(videos, start=1):
        print(f"{idx:02d}. [{item.get('categoryId')}] {item.get('title')} | author={item.get('authorName')} | recommended={item.get('isRecommended')}")
```

并在 `main()` 中调用：

```python
if args.show_recommended:
    print_video_summary(f"recommended:{account.username}", client.recommended(token))
if args.show_hot:
    print_video_summary("hot", client.hot())
```

- [ ] **Step 4: 补充脚本 README**

在 `backend/scripts/README-behavior-simulator.md` 中追加：

```md
## Summary output

加上 `--show-recommended` 可以在行为模拟后打印推荐流前 10 条。

加上 `--show-hot` 可以打印热榜前 10 条。
```

- [ ] **Step 5: 在 `backend/README.md` 增加脚本入口**

追加如下片段：

```md
## Behavior Simulator Script

如果你想快速批量模拟用户行为来测试推荐和热门，而不是手工逐个点击页面，可以使用：

```bash
python backend/scripts/simulate_behavior_profiles.py \
  --base-url http://localhost:8080 \
  --profile technology \
  --accounts tech1:password:captchaKey:captchaValue \
  --show-recommended \
  --show-hot
```

该脚本会通过现有 API 批量执行播放、进度、点赞、收藏、关注，并输出推荐流与热榜摘要。
```

- [ ] **Step 6: 做最终 smoke run**

Run（换成真实账号和验证码）:

```bash
python backend/scripts/simulate_behavior_profiles.py \
  --base-url http://localhost:8080 \
  --profile technology \
  --accounts tech1:你的密码:captchaKey:captchaValue \
  --show-recommended \
  --show-hot
```

Expected:
- 能完成行为模拟
- 能打印推荐摘要
- 能打印热榜摘要
- 退出码为 0

- [ ] **Step 7: Commit**

```bash
git add backend/scripts/simulate_behavior_profiles.py backend/scripts/README-behavior-simulator.md backend/README.md

git commit -m "feat: add batch behavior simulator"
```

---

## Spec coverage check

- 脚本位置：Task 1 明确放在 `backend/scripts/`
- 半固定画像 + 命令行参数：Task 1 完成 CLI 参数与内置模板
- 通过现有 HTTP API 模拟行为：Task 2、Task 3 完成登录与行为接口调用
- 不手写视频 ID：Task 3 通过 `/search` 和 `/video/list` 拉候选视频
- 输出推荐流和热门榜摘要：Task 4 完成
- 不修改业务接口：整份计划只新增脚本和文档

## Placeholder scan

- 没有使用 TBD / TODO / later 等占位词
- 每个任务都给出了具体代码和命令
- 后续步骤引用的函数和类型都在前文已定义

## Type consistency check

- `ProfileTemplate`、`AccountSpec`、`ApiClient` 在后续任务中命名一致
- 行为接口名 `record_play / save_progress / like / favorite / follow` 在任务间保持一致
- 推荐/热门输出函数统一使用 `print_video_summary`