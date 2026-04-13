# Obsidian QA Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Append a concise interview Q&A cards section to `D:/app包/Obsidian/Obsidian Vault/项目/Video-Streaming-Platform.md` without changing sections 1 through 9.

**Architecture:** Extend the existing Obsidian note in place by adding a new `## 10. 八股追问回答` section at the end. Organize the content into four topic groups—cache, MQ, recommendation, and search—using a stable `**Q：...** / A：...` Markdown card format, then verify heading continuity and card coverage by re-reading the file.

**Tech Stack:** Markdown, Obsidian

---

## File Map

- **Modify:** `D:/app包/Obsidian/Obsidian Vault/项目/Video-Streaming-Platform.md` — append the new Q&A card section.
- **Reference:** `docs/superpowers/specs/2026-04-13-obsidian-qa-cards-design.md` — approved design for section structure and card style.
- **Reference:** `backend/src/main/resources/CACHE_STRATEGY.md` — cache terminology and strategy alignment.
- **Reference:** `backend/src/main/resources/RECOMMENDATION_ARCHITECTURE.md` — recommendation terminology alignment.
- **Reference:** `backend/src/main/java/com/bilibili/video/service/impl/VideoCacheServiceImpl.java` — null cache, TTL jitter, lock, double delete details.
- **Reference:** `backend/src/main/java/com/bilibili/video/mq/SearchSyncConsumer.java` — search sync consumer behavior.
- **Reference:** `backend/src/main/java/com/bilibili/video/mq/VideoProcessConsumer.java` — async processing behavior.
- **Reference:** `backend/src/main/java/com/bilibili/video/mq/VideoDeleteConsumer.java` — delete cleanup behavior.
- **Reference:** `backend/src/main/java/com/bilibili/video/service/impl/SearchServiceImpl.java` — ES-only search path.
- **Reference:** `backend/src/main/java/com/bilibili/video/service/impl/RecommendationServiceImpl.java` — recall, scoring, rerank details.

### Task 1: Append section 10 with Q&A cards

**Files:**
- Modify: `D:/app包/Obsidian/Obsidian Vault/项目/Video-Streaming-Platform.md`
- Reference: `docs/superpowers/specs/2026-04-13-obsidian-qa-cards-design.md`

- [ ] **Step 1: Append the exact section 10 Markdown to the end of the file**

Append the following Markdown after the current end of the file:

```md
## 10. 八股追问回答

### 10.1 缓存相关

**Q：为什么要用两级缓存，而不是只用 Redis？**  
A：两级缓存的核心是把不同层级的性能优势结合起来：Caffeine 解决本机热点访问速度问题，Redis 解决分布式场景下的数据共享问题。只用 Redis 也能缓存，但每次都要走网络；在我这个项目里，视频详情是高频读场景，所以先查本地缓存，再查 Redis，收益会更明显。

**Q：缓存一致性怎么保证？**  
A：缓存和数据库很难做到强一致，工程上更常见的是保证最终一致性。我这个项目里主要采用“更新数据库后主动删缓存”的方式，必要时再结合延迟双删，把脏数据窗口尽量压小。

**Q：为什么选择删缓存而不是更新缓存？**  
A：因为视频详情缓存的不是单表字段，而是聚合后的 VideoVO，直接更新缓存意味着写路径要知道很多组装细节，复杂度很高。删缓存虽然看起来没那么“优雅”，但实现简单、出错面更小，也更适合当前项目阶段。

**Q：什么是缓存穿透、击穿、雪崩？你项目里怎么处理？**  
A：缓存穿透是查不存在的数据，击穿是热点 key 失效瞬间大量并发回源，雪崩是大量 key 同时过期。在我这个项目里，我用空值缓存处理穿透，用 Redis 分布式锁限制击穿，再通过 TTL 抖动避免同一时刻大批量过期。

**Q：为什么视频详情适合做缓存？**  
A：因为它是典型的读多写少场景，而且一次查询往往不只是查视频表，还要组装作者、统计值、互动状态等信息。也就是说它不只是查询频率高，单次回源成本也高，所以缓存收益很明显。

### 10.2 MQ 相关

**Q：为什么要引入 RocketMQ？**  
A：因为有些任务不值得阻塞主请求，比如视频处理、封面处理、搜索索引同步、通知下发、资源清理等。引入 RocketMQ 之后，主流程只负责关键状态更新，后置动作交给 Consumer 异步处理，整体响应时间和模块耦合度都会更好。

**Q：哪些业务适合异步，哪些不适合？**  
A：适合异步的通常是后置处理、可重试、对用户瞬时响应不敏感的任务，比如发通知、建索引、清理资源。不适合异步的是那些直接决定接口返回结果的核心写操作，比如主业务落库、权限校验、关键状态变更，这些最好还是同步完成。

**Q：异步解耦的核心收益是什么？**  
A：第一个收益是主链路更快，因为不会被非核心耗时操作拖住；第二个收益是模块之间更松耦合，新增一个后置能力时，不需要一直改主业务代码。对这个项目来说，搜索、通知、资源清理这些链路能独立出来，本身就是很大的工程收益。

**Q：消息重复消费怎么处理？**  
A：消息系统天然要考虑至少一次投递，所以消费者一般都要做幂等。在我这个项目里，Consumer 里统一走了 `consumeWithIdempotency(...)` 这类幂等包装，避免重复消费把同一个任务执行多次。

**Q：如果消息消费失败怎么办？**  
A：消费失败首先要让消息具备重试能力，所以消费者会配置最大重试次数；超过次数还失败，就应该进入告警或人工排查流程。对我来说，关键点不是“永不失败”，而是失败后系统要能重试、能观测、能兜底。

### 10.3 推荐相关

**Q：为什么推荐不能只用热门排序？**  
A：只用热门排序会有两个问题：内容容易越来越单一，新内容也很难拿到曝光。视频平台如果只有热门榜，本质上是在放大头部内容，所以我在项目里引入了新鲜、标签兴趣、分类偏好、作者偏好、运营推荐等多路召回，保证覆盖面。

**Q：为什么要拆成召回、排序、重排三层？**  
A：因为这三层解决的问题不一样：召回负责把候选尽量找全，排序负责提升相关性，重排负责控制页面观感。拆开之后，每一层都更容易单独调优，也更容易向面试官讲清楚推荐链路。

**Q：召回通道为什么要多路并行？**  
A：单一路径的候选范围通常很窄，比如只看热门会缺少个性化，只看兴趣又可能过拟合。多路召回本质上是在不同维度找候选，然后再统一打分，这样结果会更稳，也更适合冷启动和内容探索。

**Q：为什么当前阶段用线性模型而不是复杂模型？**  
A：项目早期最重要的不是把模型做得多复杂，而是先把链路搭完整、把特征沉淀下来。线性模型的优点是可解释、可调权、可快速验证，对中小项目非常实用，后面如果数据量和团队成熟度上来了，再升级模型也有基础。

**Q：重排的意义是什么？**  
A：排序后的结果不一定适合直接展示，因为可能会出现同作者、同分类内容连续扎堆。重排本质上是在保证分数基本合理的前提下，进一步优化页面丰富度和用户观感，这一步对 feed 类产品特别重要。

### 10.4 搜索相关

**Q：为什么搜索不用 MySQL 模糊查询，而要用 Elasticsearch？**  
A：MySQL 更适合事务型业务查询，不适合承接复杂检索和大规模文本搜索；而 Elasticsearch 天然就是为搜索场景设计的。对视频平台来说，标题和描述检索是高频需求，所以把搜索独立交给 ES 更合理。

**Q：搜索索引为什么要异步同步？**  
A：因为索引更新本质上是搜索系统内部维护动作，不应该阻塞主业务请求。我的做法是业务数据变更后发 MQ 消息，再由 SearchSyncConsumer 去更新或删除 ES 索引，这样主链路更轻，也更方便控制失败重试。

**Q：怎么理解业务数据和搜索索引的一致性？**  
A：这两者通常追求的是最终一致，而不是强一致，因为搜索索引本质上是面向检索的副本。工程上更现实的做法是让主业务数据先成功，再通过异步方式把索引补齐，只要延迟可控、失败可重试，这个方案就是成立的。

**Q：热门搜索和搜索历史有什么意义？**  
A：热门搜索反映的是平台整体热点，可以提升用户发现内容的效率；搜索历史反映的是个人搜索习惯，能降低重复输入成本。它们不只是“附加功能”，其实也是搜索模块产品化的一部分。

**Q：你这个搜索链路的核心优化点是什么？**  
A：我觉得核心有两个：一个是把搜索能力从 MySQL 主链路里解耦出来，让 Elasticsearch 专门负责检索；另一个是把索引同步异步化，避免业务写路径被搜索更新拖慢。这样既提升了搜索能力，也把系统职责边界划清楚了。
```

- [ ] **Step 2: Read the tail of the file and verify the new section exists**

Read: `D:/app包/Obsidian/Obsidian Vault/项目/Video-Streaming-Platform.md` (tail section)

Expected verification:
- Section `## 10. 八股追问回答` exists at the end of the file
- Subsections `### 10.1` to `### 10.4` are present
- There are 20 Q&A cards total
- Section 9 content remains intact above the new section

### Task 2: Verify formatting, scope, and interview usefulness

**Files:**
- Modify: `D:/app包/Obsidian/Obsidian Vault/项目/Video-Streaming-Platform.md` (only if verification finds issues)

- [ ] **Step 1: Verify card formatting consistency**

Check these exact conditions in the appended section:

```text
- Every card starts with "**Q："
- Every answer starts with "A："
- Each question is followed by exactly one answer
- No Mermaid blocks were added in section 10
```

Expected result: all cards use the same format.

- [ ] **Step 2: Verify topic coverage matches the approved design**

Check that section 10 includes exactly these topic groups:

```text
10.1 缓存相关
10.2 MQ 相关
10.3 推荐相关
10.4 搜索相关
```

Expected result: all four groups exist and no unrelated topic group was added.

- [ ] **Step 3: Verify wording style and placeholder absence**

Check these exact conditions:

```text
- Answers stay within concise interview-review style (roughly 2-4 sentences each)
- Answers combine principle + project-specific handling
- No placeholder text such as TODO / TBD / 待补充 / 稍后完善
- No claims that contradict current project code or docs
```

Expected result: wording is concise, project-grounded, and complete.
