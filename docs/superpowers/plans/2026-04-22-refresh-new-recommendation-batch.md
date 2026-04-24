# Refresh New Recommendation Batch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为首页推荐页增加“换一批”能力，让前后端协作尽量返回一批当前页未展示、且尽量没看过的视频，而不是简单重复第一页数据。

**Architecture:** 在推荐页前端维护已展示推荐视频 ID 集合，并在点击“换一批”时通过 `excludeVideoIds` 传给后端。后端在保持现有推荐召回、打分、重排主链路不变的前提下，对最终候选结果应用轻量排除与补齐逻辑，只在推荐页生效，不影响热门和最新页。

**Tech Stack:** Vue 3, Vue Router, Spring Boot 3, MyBatis-Plus, JUnit 5, Vitest

---

## File Structure

### New files
- `frontend/src/utils/recommendationRefresh.js` — 前端推荐页刷新相关的状态与结果合并辅助逻辑
- `frontend/tests/recommendationRefresh.test.js` — 前端“换一批”状态与合并逻辑测试

### Modified files
- `frontend/src/views/Home.vue` — 在推荐 tab 增加“换一批”按钮、维护 `seenRecommendedIds`，并在刷新时带上 `excludeVideoIds`
- `frontend/src/api/video.js` — 为 `getRecommended` 增加可选 `excludeVideoIds` 参数透传
- `backend/src/main/java/com/bilibili/video/controller/VideoController.java` — 推荐接口增加可选 `excludeVideoIds` 参数
- `backend/src/main/java/com/bilibili/video/service/VideoService.java` — 推荐服务接口增加 `excludeVideoIds`
- `backend/src/main/java/com/bilibili/video/service/RecommendationService.java` — 推荐领域服务接口增加 `excludeVideoIds`
- `backend/src/main/java/com/bilibili/video/service/impl/VideoServiceImpl.java` — 透传 `excludeVideoIds` 到推荐服务
- `backend/src/main/java/com/bilibili/video/service/impl/VideoQueryService.java` — 透传 `excludeVideoIds` 到推荐服务
- `backend/src/main/java/com/bilibili/video/service/impl/RecommendationServiceImpl.java` — 在最终结果阶段过滤 `excludeVideoIds` 并补齐结果
- `backend/src/test/java/com/bilibili/video/service/impl/RecommendationServiceImplTest.java` — 后端推荐“排除当前已展示视频”行为测试（如当前不存在该测试文件则新建）

### Test files
- `frontend/tests/recommendationRefresh.test.js`
- `backend/src/test/java/com/bilibili/video/service/impl/RecommendationServiceImplTest.java`

---

### Task 1: 定义前端“换一批”状态与合并工具

**Files:**
- Create: `frontend/src/utils/recommendationRefresh.js`
- Test: `frontend/tests/recommendationRefresh.test.js`

- [ ] **Step 1: 写失败测试，锁定推荐页已展示 ID 记录和新结果替换逻辑**

```javascript
import { describe, expect, it } from 'vitest'
import { appendSeenIds, replaceRecommendationBatch } from '../src/utils/recommendationRefresh'

describe('recommendation refresh helpers', () => {
  it('should append unique seen ids and replace current batch', () => {
    const seen = appendSeenIds([], [{ id: 1 }, { id: 2 }, { id: 2 }, { id: 3 }])
    expect(seen).toEqual([1, 2, 3])

    const next = replaceRecommendationBatch(
      [{ id: 1, title: 'old-1' }, { id: 2, title: 'old-2' }],
      [{ id: 4, title: 'new-4' }, { id: 5, title: 'new-5' }]
    )
    expect(next).toEqual([
      { id: 4, title: 'new-4' },
      { id: 5, title: 'new-5' }
    ])
  })

  it('should ignore invalid ids when collecting seen items', () => {
    const seen = appendSeenIds([1], [{ id: 1 }, { id: null }, {}, { id: 3 }])
    expect(seen).toEqual([1, 3])
  })
})
```

- [ ] **Step 2: 运行测试确认当前失败**

Run:

```bash
cd frontend && npm run test -- recommendationRefresh.test.js
```

Expected:
- FAIL
- 提示 `appendSeenIds` / `replaceRecommendationBatch` 未定义或文件不存在

- [ ] **Step 3: 写最小实现**

`frontend/src/utils/recommendationRefresh.js`

```javascript
export function appendSeenIds(existingIds, videos) {
  const result = Array.isArray(existingIds) ? [...existingIds] : []
  const seen = new Set(result)
  for (const video of Array.isArray(videos) ? videos : []) {
    const id = video?.id
    if (typeof id === 'number' && !seen.has(id)) {
      seen.add(id)
      result.push(id)
    }
  }
  return result
}

export function replaceRecommendationBatch(_currentVideos, nextVideos) {
  return Array.isArray(nextVideos) ? [...nextVideos] : []
}
```

- [ ] **Step 4: 重新运行测试确认通过**

Run:

```bash
cd frontend && npm run test -- recommendationRefresh.test.js
```

Expected:
- PASS
- `recommendationRefresh.test.js` 通过

- [ ] **Step 5: Commit**

```bash
git add frontend/src/utils/recommendationRefresh.js frontend/tests/recommendationRefresh.test.js

git commit -m "feat: add recommendation refresh helpers"
```

### Task 2: 前端推荐页接入“换一批”按钮与状态

**Files:**
- Modify: `frontend/src/views/Home.vue`
- Modify: `frontend/src/api/video.js`
- Create/Modify: `frontend/tests/recommendationRefresh.test.js`

- [ ] **Step 1: 写一个失败测试，锁定 `getRecommended` 可带 `excludeVideoIds` 参数**

在 `frontend/tests/recommendationRefresh.test.js` 追加：

```javascript
import { describe, expect, it, vi } from 'vitest'

vi.mock('../src/api/request', () => {
  return {
    default: {
      get: vi.fn((url, config) => ({ url, config }))
    }
  }
})

import { getRecommended } from '../src/api/video'

it('should pass excludeVideoIds to recommended api params', () => {
  const result = getRecommended(1, 12, [10, 11, 12])
  expect(result.config.params).toEqual({
    page: 1,
    size: 12,
    excludeVideoIds: '10,11,12'
  })
})
```

- [ ] **Step 2: 运行测试确认当前失败**

Run:

```bash
cd frontend && npm run test -- recommendationRefresh.test.js
```

Expected:
- FAIL
- `excludeVideoIds` 不存在或参数不匹配

- [ ] **Step 3: 修改 API 封装，支持 `excludeVideoIds`**

把 `frontend/src/api/video.js` 中的 `getRecommended` 改成：

```javascript
export const getRecommended = (page = 1, size = 12, excludeVideoIds = []) =>
  request.get('/video/recommended', {
    params: {
      page,
      size,
      excludeVideoIds: Array.isArray(excludeVideoIds) && excludeVideoIds.length > 0
        ? excludeVideoIds.join(',')
        : undefined
    }
  })
```

- [ ] **Step 4: 在首页推荐页增加状态与按钮**

在 `frontend/src/views/Home.vue` 中：

1. 引入工具函数：

```javascript
import { appendSeenIds, replaceRecommendationBatch } from '../utils/recommendationRefresh'
```

2. 新增状态：

```javascript
const seenRecommendedIds = ref([])
const refreshingRecommend = ref(false)
```

3. 修改 `fetchApi`：

```javascript
const fetchApi = (p, excludeIds = []) => {
  if (activeTab.value === 'hot') return getHotList(p, pageSize)
  return activeTab.value === 'recommend'
    ? getRecommended(p, pageSize, excludeIds)
    : getVideoList(p, pageSize)
}
```

4. 修改 `fetchList`，在推荐 tab 下记录已展示 ID：

```javascript
async function fetchList(isMore = false, excludeIds = []) {
  if (loading.value) return
  loading.value = true
  if (!isMore) error.value = ''
  try {
    const res = await fetchApi(isMore ? page.value : 1, excludeIds)
    const list = res.records || []
    const newList = list.map(item => ({ ...item, isLoaded: false }))
    if (isMore) videoList.value.push(...newList)
    else videoList.value = newList

    if (activeTab.value === 'recommend') {
      seenRecommendedIds.value = appendSeenIds(seenRecommendedIds.value, newList)
    }

    hasMore.value = res.current < res.pages
    page.value = isMore ? page.value + 1 : 2
  } catch (e) {
    console.error('Failed to fetch video list:', e)
    if (!isMore) {
      videoList.value = mockVideoData
      hasMore.value = false
      error.value = ''
    }
  } finally {
    loading.value = false
    initObserver()
  }
}
```

5. 新增“换一批”方法：

```javascript
async function refreshRecommendBatch() {
  if (activeTab.value !== 'recommend' || refreshingRecommend.value) return
  refreshingRecommend.value = true
  try {
    const res = await getRecommended(1, pageSize, seenRecommendedIds.value)
    const nextRecords = Array.isArray(res?.records) ? res.records : []
    const nextBatch = nextRecords.map(item => ({ ...item, isLoaded: false }))
    videoList.value = replaceRecommendationBatch(videoList.value, nextBatch)
    seenRecommendedIds.value = appendSeenIds(seenRecommendedIds.value, nextBatch)
    hasMore.value = res.current < res.pages
    page.value = 2
  } catch (e) {
    console.error('Failed to refresh recommendation batch:', e)
  } finally {
    refreshingRecommend.value = false
    initObserver()
  }
}
```

6. 在推荐 tab 区域增加按钮（紧跟 tab 区）：

```vue
<button
  v-if="activeTab === 'recommend'"
  @click="refreshRecommendBatch"
  :disabled="refreshingRecommend"
  class="refresh-batch-btn"
>
  {{ refreshingRecommend ? '刷新中...' : '换一批' }}
</button>
```

7. 在切 tab / route 切换时重置状态：

```javascript
function switchTab(tab) {
  activeTab.value = tab
  page.value = 1
  if (tab !== 'recommend') {
    seenRecommendedIds.value = []
  }
  fetchList(false)
}

watch(() => route.query, () => {
  syncFromQuery()
  page.value = 1
  if (activeTab.value !== 'recommend') {
    seenRecommendedIds.value = []
  }
  fetchList(false)
}, { immediate: true })
```
```

8. 添加最小样式：

```css
.refresh-batch-btn {
  padding: 10px 18px;
  border: none;
  border-radius: 999px;
  background: #fb7299;
  color: white;
  font-weight: 700;
  cursor: pointer;
}
.refresh-batch-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
```

- [ ] **Step 5: 运行前端测试确认通过**

Run:

```bash
cd frontend && npm run test -- recommendationRefresh.test.js
```

Expected:
- PASS
- API 参数透传与 helper 测试通过

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/video.js frontend/src/views/Home.vue frontend/src/utils/recommendationRefresh.js frontend/tests/recommendationRefresh.test.js

git commit -m "feat: add refresh batch for recommended feed"
```

### Task 3: 后端推荐接口支持排除当前页视频 ID

**Files:**
- Modify: `backend/src/main/java/com/bilibili/video/controller/VideoController.java`
- Modify: `backend/src/main/java/com/bilibili/video/service/VideoService.java`
- Modify: `backend/src/main/java/com/bilibili/video/service/RecommendationService.java`
- Modify: `backend/src/main/java/com/bilibili/video/service/impl/VideoServiceImpl.java`
- Modify: `backend/src/main/java/com/bilibili/video/service/impl/VideoQueryService.java`
- Modify: `backend/src/main/java/com/bilibili/video/service/impl/RecommendationServiceImpl.java`
- Test: `backend/src/test/java/com/bilibili/video/service/impl/RecommendationServiceImplTest.java`

- [ ] **Step 1: 写失败测试，锁定 `excludeVideoIds` 的过滤与补齐行为**

新建 `backend/src/test/java/com/bilibili/video/service/impl/RecommendationServiceImplTest.java`：

```java
package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bilibili.video.entity.Video;
import com.bilibili.video.mapper.FavoriteMapper;
import com.bilibili.video.mapper.FollowMapper;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.VideoTagFeatureMapper;
import com.bilibili.video.mapper.WatchHistoryMapper;
import com.bilibili.video.service.RecExposureLogService;
import com.bilibili.video.service.RecommendationFeatureService;
import com.bilibili.video.service.UserProfileSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceImplTest {

    @Test
    void shouldExcludeCurrentBatchIdsAndStillFillPage() {
        VideoMapper videoMapper = mock(VideoMapper.class);
        VideoTagFeatureMapper videoTagFeatureMapper = mock(VideoTagFeatureMapper.class);
        WatchHistoryMapper watchHistoryMapper = mock(WatchHistoryMapper.class);
        VideoLikeMapper videoLikeMapper = mock(VideoLikeMapper.class);
        FavoriteMapper favoriteMapper = mock(FavoriteMapper.class);
        FollowMapper followMapper = mock(FollowMapper.class);
        RecommendationFeatureService recommendationFeatureService = mock(RecommendationFeatureService.class);
        UserProfileSummaryService userProfileSummaryService = mock(UserProfileSummaryService.class);
        VideoViewAssembler assembler = mock(VideoViewAssembler.class);
        RecExposureLogService exposureLogService = mock(RecExposureLogService.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

        RecommendationServiceImpl service = new RecommendationServiceImpl(
                videoMapper,
                videoTagFeatureMapper,
                watchHistoryMapper,
                videoLikeMapper,
                favoriteMapper,
                followMapper,
                recommendationFeatureService,
                userProfileSummaryService,
                assembler,
                exposureLogService,
                redisTemplate
        );

        List<Video> videos = List.of(
                video(1L, true, 100),
                video(2L, true, 99),
                video(3L, true, 98),
                video(4L, false, 97),
                video(5L, false, 96)
        );

        when(videoMapper.selectList(any())).thenReturn(videos);
        when(videoMapper.selectBatchIds(any())).thenReturn(videos);
        when(assembler.toVideoVOList(any(), any())).thenAnswer(invocation ->
                ((List<Video>) invocation.getArgument(0)).stream().map(video -> {
                    var vo = new com.bilibili.video.model.vo.VideoVO();
                    vo.setId(video.getId());
                    vo.setTitle(video.getTitle());
                    return vo;
                }).toList()
        );

        IPage<com.bilibili.video.model.vo.VideoVO> page = service.listRecommended(1, 3, 1L, Set.of(1L, 2L));

        assertThat(page.getRecords()).extracting(com.bilibili.video.model.vo.VideoVO::getId)
                .doesNotContain(1L, 2L)
                .hasSize(3);
    }

    private Video video(Long id, boolean recommended, long playCount) {
        Video video = new Video();
        video.setId(id);
        video.setTitle("video-" + id);
        video.setAuthorId(id);
        video.setCategoryId(1L);
        video.setIsRecommended(recommended);
        video.setPlayCount(playCount);
        video.setLikeCount(0L);
        video.setSaveCount(0L);
        video.setCreateTime(LocalDateTime.now().minusDays(id));
        return video;
    }
}
```

- [ ] **Step 2: 运行测试确认当前失败**

Run:

```bash
cd backend && mvn -Dtest=RecommendationServiceImplTest test
```

Expected:
- FAIL
- `listRecommended(..., Set<Long>)` 方法不存在或签名不匹配

- [ ] **Step 3: 扩展 controller / service / recommendation 接口**

1. `VideoController.recommended(...)` 改成：

```java
@GetMapping("/recommended")
public Result<IPage<VideoVO>> recommended(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "12") int size,
        @RequestParam(required = false) String excludeVideoIds) {
    Long userId = UserContext.get();
    return Result.success(videoService.listRecommended(page, size, userId, parseExcludeIds(excludeVideoIds)));
}
```

并补：

```java
private Set<Long> parseExcludeIds(String excludeVideoIds) {
    if (excludeVideoIds == null || excludeVideoIds.isBlank()) {
        return Collections.emptySet();
    }
    return Arrays.stream(excludeVideoIds.split(","))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .limit(200)
            .map(item -> {
                try {
                    return Long.valueOf(item);
                } catch (NumberFormatException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
}
```

2. `VideoService` 增加：

```java
IPage<VideoVO> listRecommended(int page, int size, Long userId, Set<Long> excludeVideoIds);
```

3. `RecommendationService` 增加：

```java
IPage<VideoVO> listRecommended(int page, int size, Long userId, Set<Long> excludeVideoIds);
```

4. `VideoServiceImpl` / `VideoQueryService` 透传参数。

- [ ] **Step 4: 在推荐结果最终输出阶段做过滤与补齐**

在 `RecommendationServiceImpl` 中：

1. 保留现有 `listRecommended(int page, int size, Long userId)`，让它转调新重载：

```java
@Override
public IPage<VideoVO> listRecommended(int page, int size, Long userId) {
    return listRecommended(page, size, userId, Collections.emptySet());
}
```

2. 新增重载：

```java
@Override
public IPage<VideoVO> listRecommended(int page, int size, Long userId, Set<Long> excludeVideoIds) {
    int safePage = Math.max(page, 1);
    int safeSize = Math.max(1, Math.min(size, 50));
    Set<Long> safeExcludeIds = excludeVideoIds == null ? Collections.emptySet() : excludeVideoIds;
    int windowSize = RedisConstants.RECOMMEND_RESULT_WINDOW_SIZE;

    CachedRecommendationWindow cachedWindow = getCachedWindow(userId, windowSize);
    if (cachedWindow != null) {
        return buildPageFromCachedWindow(applyExcludeIds(cachedWindow, safeExcludeIds), safePage, safeSize, userId);
    }

    CachedRecommendationWindow built = buildRecommendationWindow(userId, windowSize);
    cacheRecommendationWindow(userId, windowSize, built);
    return buildPageFromCachedWindow(applyExcludeIds(built, safeExcludeIds), safePage, safeSize, userId);
}
```

3. 新增过滤与补齐逻辑（最小实现）：

```java
private CachedRecommendationWindow applyExcludeIds(CachedRecommendationWindow window, Set<Long> excludeVideoIds) {
    if (window == null || excludeVideoIds == null || excludeVideoIds.isEmpty()) {
        return window;
    }
    List<Long> originalIds = window.getIds() == null ? Collections.emptyList() : window.getIds();
    List<Long> filteredIds = originalIds.stream()
            .filter(id -> !excludeVideoIds.contains(id))
            .toList();

    if (filteredIds.size() < originalIds.size()) {
        for (Long id : originalIds) {
            if (filteredIds.size() >= originalIds.size()) {
                break;
            }
            if (!filteredIds.contains(id) && !excludeVideoIds.contains(id)) {
                filteredIds = new ArrayList<>(filteredIds);
                ((ArrayList<Long>) filteredIds).add(id);
            }
        }
    }

    return new CachedRecommendationWindow(
            filteredIds,
            window.getTotal(),
            window.isHasMore(),
            window.getGeneratedAt(),
            window.getScoreById()
    );
}
```

若当前 `CachedRecommendationWindow` 字段名与这里不同，以实际字段名为准，但语义必须一致。

- [ ] **Step 5: 运行后端测试确认通过**

Run:

```bash
cd backend && mvn -Dtest=RecommendationServiceImplTest test
```

Expected:
- PASS
- 过滤和补齐测试通过

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/bilibili/video/controller/VideoController.java \
        backend/src/main/java/com/bilibili/video/service/VideoService.java \
        backend/src/main/java/com/bilibili/video/service/RecommendationService.java \
        backend/src/main/java/com/bilibili/video/service/impl/VideoServiceImpl.java \
        backend/src/main/java/com/bilibili/video/service/impl/VideoQueryService.java \
        backend/src/main/java/com/bilibili/video/service/impl/RecommendationServiceImpl.java \
        backend/src/test/java/com/bilibili/video/service/impl/RecommendationServiceImplTest.java

git commit -m "feat: support refresh batch for recommended feed"
```

### Task 4: 手动验证“换一批”体验

**Files:**
- Modify: `frontend/src/views/Home.vue` (如需微调按钮位置或文案)

- [ ] **Step 1: 启动前后端开发环境**

Run:

```bash
cd backend && mvn spring-boot:run
cd frontend && npm run dev
```

Expected:
- backend 在 `8080`
- frontend 在 `5173`

- [ ] **Step 2: 打开推荐页并记录第一批视频 ID**

手动步骤：
- 打开首页推荐 tab
- 记下首屏前 4~10 个视频 ID / 标题

Expected:
- 能看到“换一批”按钮

- [ ] **Step 3: 点击“换一批”并验证结果变化**

手动步骤：
- 点击“换一批”
- 观察当前推荐列表是否整体替换

Expected:
- 新一批推荐结果与上一批存在明显差异
- 不会只是简单重拉同一批第一页结果

- [ ] **Step 4: 验证热门 / 最新页不受影响**

手动步骤：
- 切到热门页
- 切到最新页

Expected:
- 不显示“换一批”按钮
- 行为保持原状

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/Home.vue frontend/src/api/video.js frontend/src/utils/recommendationRefresh.js frontend/tests/recommendationRefresh.test.js

git commit -m "feat: add refresh batch for recommendation page"
```

---

## Spec coverage check

- 仅推荐页做“换一批”：Task 2 明确只在推荐 tab 显示按钮
- 前后端混合方案：Task 2 负责前端状态与 UI，Task 3 负责后端过滤与补齐
- 不重写推荐算法：Task 3 只在最终结果阶段应用 `excludeVideoIds`
- 前端维护已展示 ID：Task 2 使用 `seenRecommendedIds`
- 后端接收 `excludeVideoIds`：Task 3 扩展 controller/service/recommendation 接口
- 过滤后不够时补齐：Task 3 在最终结果阶段做轻量补齐
- 热门 / 最新页不受影响：Task 2 / Task 4 明确验证

## Placeholder scan

- 没有 TBD / TODO / implement later
- 每个任务都给出了具体代码和命令
- 没有引用未定义的函数或类型名而不说明

## Type consistency check

- 前端统一使用 `seenRecommendedIds`
- 前端 helper 统一使用 `appendSeenIds` / `replaceRecommendationBatch`
- 后端统一使用 `excludeVideoIds`
- 推荐服务接口统一增加 `Set<Long> excludeVideoIds`