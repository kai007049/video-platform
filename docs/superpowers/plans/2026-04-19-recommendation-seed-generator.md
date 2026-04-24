# Recommendation Seed Generator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 backend 增加一个显式触发的一次命令式 seed generator，向现有数据库追加中等规模、偏真实分布的推荐测试数据，并补齐推荐画像、视频统计、Redis 热榜与可选搜索重建。

**Architecture:** 采用 backend 内置的 startup runner + 配置属性 + 分层 seed 服务。先用 `SeedProfileCatalog` 和 `SeedDomainCatalog` 固定规模与兴趣域，再由用户、视频、行为生成器分阶段落库，最后统一回填 `user_interest_tag` / `video_tag_feature` / 视频统计与 Redis hot ZSet，并在显式开启时执行搜索重建后自动退出。

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, MySQL, Redis, JUnit 5, Mockito

---

## File Structure

### New files

- `backend/src/main/java/com/bilibili/video/seed/SeedMode.java` — seed 运行档位枚举：`SMALL` / `MEDIUM` / `LARGE`
- `backend/src/main/java/com/bilibili/video/seed/SeedProfile.java` — 固化每个档位的用户/视频/行为数量与概率参数
- `backend/src/main/java/com/bilibili/video/seed/SeedProperties.java` — `seed.*` 启动参数绑定与显式安全校验
- `backend/src/main/java/com/bilibili/video/seed/SeedProfileCatalog.java` — 按 mode 与 override 解析最终 profile
- `backend/src/main/java/com/bilibili/video/seed/InterestCluster.java` — 兴趣域定义（分类、标签、标题词池）
- `backend/src/main/java/com/bilibili/video/seed/SeedDomainSnapshot.java` — 当前 DB 分类/标签映射快照
- `backend/src/main/java/com/bilibili/video/seed/SeedDomainCatalog.java` — 从现有 `category` / `tag` 构建兴趣域
- `backend/src/main/java/com/bilibili/video/seed/SeedUserPersona.java` — 用户活跃度枚举：`HEAVY` / `MEDIUM` / `LIGHT` / `COLD`
- `backend/src/main/java/com/bilibili/video/seed/SeedAuthorProfile.java` — 已创建作者的元数据
- `backend/src/main/java/com/bilibili/video/seed/SeedUserProfile.java` — 已创建普通用户的兴趣与活跃度元数据
- `backend/src/main/java/com/bilibili/video/seed/SeedPopulation.java` — 作者和普通用户集合
- `backend/src/main/java/com/bilibili/video/seed/SeedVideoProfile.java` — 已创建视频的聚合元数据
- `backend/src/main/java/com/bilibili/video/seed/SeedBehaviorResult.java` — 行为生成结果与聚合统计
- `backend/src/main/java/com/bilibili/video/seed/SeedUserGenerator.java` — 追加生成作者和普通用户
- `backend/src/main/java/com/bilibili/video/seed/SeedVideoGenerator.java` — 追加生成视频和 `video_tag`
- `backend/src/main/java/com/bilibili/video/seed/SeedBehaviorGenerator.java` — 基于兴趣与活跃度生成观看/点赞/收藏/关注
- `backend/src/main/java/com/bilibili/video/seed/SeedProjectionService.java` — 回填视频统计、用户兴趣、视频标签特征、Redis 热榜
- `backend/src/main/java/com/bilibili/video/seed/SeedGenerationSummary.java` — 最终输出统计摘要
- `backend/src/main/java/com/bilibili/video/seed/SeedOrchestrator.java` — 串起完整 seed 流程
- `backend/src/main/java/com/bilibili/video/seed/SeedCommandRunner.java` — 显式触发时启动执行并退出

### Modified files

- `README.md` — 在根文档补充 seed 命令入口与依赖说明
- `backend/README.md` — 在 backend 文档补充 seed 使用方式、参数与验证方法

### Test files

- `backend/src/test/java/com/bilibili/video/seed/SeedProfileCatalogTest.java`
- `backend/src/test/java/com/bilibili/video/seed/SeedDomainCatalogTest.java`
- `backend/src/test/java/com/bilibili/video/seed/SeedUserGeneratorTest.java`
- `backend/src/test/java/com/bilibili/video/seed/SeedVideoGeneratorTest.java`
- `backend/src/test/java/com/bilibili/video/seed/SeedBehaviorGeneratorTest.java`
- `backend/src/test/java/com/bilibili/video/seed/SeedProjectionServiceTest.java`
- `backend/src/test/java/com/bilibili/video/seed/SeedCommandRunnerTest.java`

---

### Task 1: Seed 配置与档位解析

**Files:**
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedMode.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedProfile.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedProperties.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedProfileCatalog.java`
- Test: `backend/src/test/java/com/bilibili/video/seed/SeedProfileCatalogTest.java`

- [ ] **Step 1: 写失败测试，锁定 medium 默认值、override 行为和显式安全校验**

```java
package com.bilibili.video.seed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedProfileCatalogTest {

    private final SeedProfileCatalog catalog = new SeedProfileCatalog();

    @Test
    void shouldResolveMediumDefaults() {
        SeedProperties properties = new SeedProperties();
        properties.setEnabled(true);
        properties.setAppend(true);
        properties.setMode(SeedMode.MEDIUM);

        SeedProfile profile = catalog.resolve(properties);

        assertThat(profile.authorCount()).isEqualTo(100);
        assertThat(profile.userCount()).isEqualTo(1000);
        assertThat(profile.videoCount()).isEqualTo(6000);
        assertThat(profile.watchCount()).isEqualTo(80000);
        assertThat(profile.likeCount()).isEqualTo(10000);
        assertThat(profile.favoriteCount()).isEqualTo(3500);
        assertThat(profile.followCount()).isEqualTo(1600);
        assertThat(profile.primaryPreferenceWeight()).isEqualTo(0.70D);
        assertThat(profile.secondaryPreferenceWeight()).isEqualTo(0.20D);
        assertThat(profile.explorationWeight()).isEqualTo(0.10D);
    }

    @Test
    void shouldApplyExplicitOverrides() {
        SeedProperties properties = new SeedProperties();
        properties.setEnabled(true);
        properties.setAppend(true);
        properties.setMode(SeedMode.SMALL);
        properties.setVideoCount(888);
        properties.setWatchCount(9999);

        SeedProfile profile = catalog.resolve(properties);

        assertThat(profile.videoCount()).isEqualTo(888);
        assertThat(profile.watchCount()).isEqualTo(9999);
        assertThat(profile.authorCount()).isEqualTo(20);
    }

    @Test
    void shouldRejectEnabledSeedWithoutAppendMode() {
        SeedProperties properties = new SeedProperties();
        properties.setEnabled(true);
        properties.setAppend(false);

        assertThatThrownBy(properties::validateOrThrow)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("append=true");
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```bash
cd backend && mvn -Dtest=SeedProfileCatalogTest test
```

Expected:
- FAIL
- `package com.bilibili.video.seed does not exist` 或 `cannot find symbol: class SeedProfileCatalog`

- [ ] **Step 3: 写最小实现，让配置与档位可解析**

`backend/src/main/java/com/bilibili/video/seed/SeedMode.java`

```java
package com.bilibili.video.seed;

public enum SeedMode {
    SMALL,
    MEDIUM,
    LARGE
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedProfile.java`

```java
package com.bilibili.video.seed;

public record SeedProfile(
        int authorCount,
        int userCount,
        int videoCount,
        int watchCount,
        int likeCount,
        int favoriteCount,
        int followCount,
        double heavyUserRatio,
        double mediumUserRatio,
        double lightUserRatio,
        double coldUserRatio,
        double primaryPreferenceWeight,
        double secondaryPreferenceWeight,
        double explorationWeight,
        int recentDaysWindow
) {
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedProperties.java`

```java
package com.bilibili.video.seed;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "seed")
public class SeedProperties {

    private boolean enabled = false;
    private boolean append = false;
    private boolean searchReindex = false;
    private SeedMode mode = SeedMode.MEDIUM;
    private Long randomSeed = 42L;

    private Integer authorCount;
    private Integer userCount;
    private Integer videoCount;
    private Integer watchCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer followCount;

    public void validateOrThrow() {
        if (enabled && !append) {
            throw new IllegalStateException("Seed generation only supports append=true");
        }
    }
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedProfileCatalog.java`

```java
package com.bilibili.video.seed;

import org.springframework.stereotype.Component;

@Component
public class SeedProfileCatalog {

    public SeedProfile resolve(SeedProperties properties) {
        SeedProfile defaults = switch (properties.getMode()) {
            case SMALL -> new SeedProfile(20, 120, 400, 2000, 250, 100, 50,
                    0.10D, 0.30D, 0.45D, 0.15D,
                    0.70D, 0.20D, 0.10D, 30);
            case MEDIUM -> new SeedProfile(100, 1000, 6000, 80000, 10000, 3500, 1600,
                    0.12D, 0.33D, 0.40D, 0.15D,
                    0.70D, 0.20D, 0.10D, 90);
            case LARGE -> new SeedProfile(300, 4000, 25000, 400000, 60000, 20000, 9000,
                    0.12D, 0.33D, 0.40D, 0.15D,
                    0.70D, 0.20D, 0.10D, 120);
        };

        return new SeedProfile(
                pick(properties.getAuthorCount(), defaults.authorCount()),
                pick(properties.getUserCount(), defaults.userCount()),
                pick(properties.getVideoCount(), defaults.videoCount()),
                pick(properties.getWatchCount(), defaults.watchCount()),
                pick(properties.getLikeCount(), defaults.likeCount()),
                pick(properties.getFavoriteCount(), defaults.favoriteCount()),
                pick(properties.getFollowCount(), defaults.followCount()),
                defaults.heavyUserRatio(),
                defaults.mediumUserRatio(),
                defaults.lightUserRatio(),
                defaults.coldUserRatio(),
                defaults.primaryPreferenceWeight(),
                defaults.secondaryPreferenceWeight(),
                defaults.explorationWeight(),
                defaults.recentDaysWindow()
        );
    }

    private int pick(Integer overrideValue, int defaultValue) {
        return overrideValue == null || overrideValue <= 0 ? defaultValue : overrideValue;
    }
}
```

- [ ] **Step 4: 重新运行测试，确认通过**

Run:

```bash
cd backend && mvn -Dtest=SeedProfileCatalogTest test
```

Expected:
- PASS
- `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: 提交这一小步**

```bash
git add backend/src/main/java/com/bilibili/video/seed/SeedMode.java \
        backend/src/main/java/com/bilibili/video/seed/SeedProfile.java \
        backend/src/main/java/com/bilibili/video/seed/SeedProperties.java \
        backend/src/main/java/com/bilibili/video/seed/SeedProfileCatalog.java \
        backend/src/test/java/com/bilibili/video/seed/SeedProfileCatalogTest.java

git commit -m "feat(seed): add seed profile configuration"
```

### Task 2: 从现有分类和标签构建兴趣域

**Files:**
- Create: `backend/src/main/java/com/bilibili/video/seed/InterestCluster.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedDomainSnapshot.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedDomainCatalog.java`
- Test: `backend/src/test/java/com/bilibili/video/seed/SeedDomainCatalogTest.java`

- [ ] **Step 1: 写失败测试，锁定兴趣域构建规则和缺失校验**

```java
package com.bilibili.video.seed;

import com.bilibili.video.entity.Category;
import com.bilibili.video.entity.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedDomainCatalogTest {

    @Test
    void shouldBuildGamingAndTechnologyClustersFromExistingNames() {
        SeedDomainCatalog catalog = new SeedDomainCatalog(null, null);

        SeedDomainSnapshot snapshot = catalog.buildSnapshot(
                List.of(
                        category(1L, "游戏"),
                        category(2L, "电子竞技"),
                        category(3L, "科技"),
                        category(4L, "编程"),
                        category(5L, "生活")
                ),
                List.of(
                        tag(11L, "游戏"),
                        tag(12L, "手游"),
                        tag(13L, "科技"),
                        tag(14L, "Java"),
                        tag(15L, "Vlog")
                )
        );

        assertThat(snapshot.clusters()).extracting(InterestCluster::key)
                .contains("gaming", "technology");
        assertThat(snapshot.requireCluster("gaming").tagIds()).containsExactlyInAnyOrder(11L, 12L);
        assertThat(snapshot.requireCluster("technology").tagIds()).containsExactlyInAnyOrder(13L, 14L);
    }

    @Test
    void shouldFailWhenNoClusterCanBeBuilt() {
        SeedDomainCatalog catalog = new SeedDomainCatalog(null, null);

        assertThatThrownBy(() -> catalog.buildSnapshot(List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No usable seed clusters");
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private Tag tag(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```bash
cd backend && mvn -Dtest=SeedDomainCatalogTest test
```

Expected:
- FAIL
- `cannot find symbol: class SeedDomainCatalog` / `class InterestCluster`

- [ ] **Step 3: 写最小实现，让兴趣域可由 schema 现有名称映射出来**

`backend/src/main/java/com/bilibili/video/seed/InterestCluster.java`

```java
package com.bilibili.video.seed;

import java.util.List;

public record InterestCluster(
        String key,
        String displayName,
        List<Long> categoryIds,
        List<Long> tagIds,
        List<String> titlePrefixes,
        List<String> titleKeywords
) {
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedDomainSnapshot.java`

```java
package com.bilibili.video.seed;

import java.util.List;
import java.util.Map;

public record SeedDomainSnapshot(
        List<InterestCluster> clusters,
        Map<String, InterestCluster> clusterByKey,
        List<Long> allCategoryIds,
        List<Long> allTagIds
) {
    public InterestCluster requireCluster(String key) {
        InterestCluster cluster = clusterByKey.get(key);
        if (cluster == null) {
            throw new IllegalArgumentException("Unknown cluster: " + key);
        }
        return cluster;
    }
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedDomainCatalog.java`

```java
package com.bilibili.video.seed;

import com.bilibili.video.entity.Category;
import com.bilibili.video.entity.Tag;
import com.bilibili.video.mapper.CategoryMapper;
import com.bilibili.video.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SeedDomainCatalog {

    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;

    public SeedDomainSnapshot load() {
        return buildSnapshot(categoryMapper.selectList(null), tagMapper.selectList(null));
    }

    SeedDomainSnapshot buildSnapshot(List<Category> categories, List<Tag> tags) {
        Map<String, Long> categoryIdByName = categories.stream()
                .filter(item -> item.getName() != null)
                .collect(Collectors.toMap(Category::getName, Category::getId, (a, b) -> a, LinkedHashMap::new));
        Map<String, Long> tagIdByName = tags.stream()
                .filter(item -> item.getName() != null)
                .collect(Collectors.toMap(Tag::getName, Tag::getId, (a, b) -> a, LinkedHashMap::new));

        List<InterestCluster> clusters = new ArrayList<>();
        clusters.add(cluster("animation", "动画", categoryIdByName, tagIdByName,
                List.of("动画", "二次元", "鬼畜"),
                List.of("动画", "二次元", "鬼畜", "宅舞", "绘画"),
                List.of("番剧", "MAD", "剪辑", "安利"),
                List.of("高能", "必看", "收藏向")));
        clusters.add(cluster("gaming", "游戏", categoryIdByName, tagIdByName,
                List.of("游戏", "电子竞技", "手游", "单机游戏", "主机游戏", "攻略"),
                List.of("游戏", "电子竞技", "手游", "单机游戏", "主机游戏", "实况解说", "攻略"),
                List.of("上分", "开荒", "实况", "版本答案"),
                List.of("教学", "名场面", "整活")));
        clusters.add(cluster("technology", "科技", categoryIdByName, tagIdByName,
                List.of("科技", "编程", "前端", "后端", "人工智能", "机器学习", "数码"),
                List.of("科技", "编程", "前端", "后端", "Java", "SpringBoot", "Vue", "React", "MySQL", "Redis", "Docker", "人工智能", "机器学习", "评测", "开箱"),
                List.of("实战", "避坑", "入门", "拆解"),
                List.of("教程", "面试", "项目")));
        clusters.add(cluster("music", "音乐", categoryIdByName, tagIdByName,
                List.of("音乐", "翻唱", "舞蹈", "说唱", "宅舞"),
                List.of("音乐", "翻唱", "舞蹈", "说唱", "原声", "宅舞"),
                List.of("翻唱", "现场", "纯享", "改编"),
                List.of("高音", "混剪", "循环")));
        clusters.add(cluster("sports", "体育", categoryIdByName, tagIdByName,
                List.of("体育", "篮球", "足球", "NBA", "CBA", "欧冠", "英超", "世界杯"),
                List.of("体育", "篮球", "足球", "NBA", "CBA", "欧冠", "英超", "世界杯", "集锦", "赛事", "体育解说"),
                List.of("复盘", "集锦", "高光", "战术"),
                List.of("绝杀", "三分", "球星")));
        clusters.add(cluster("lifestyle", "生活", categoryIdByName, tagIdByName,
                List.of("生活", "日常", "校园", "健身", "穿搭", "宠物", "旅行", "美食", "Vlog"),
                List.of("生活", "日常", "校园", "健身", "穿搭", "宠物", "旅行", "美食", "Vlog", "摄影"),
                List.of("一天", "周末", "探店", "记录"),
                List.of("日常", "治愈", "出行")));
        clusters.add(cluster("film", "影视", categoryIdByName, tagIdByName,
                List.of("影视", "电影", "电视剧", "纪录片", "影视解说", "娱乐", "搞笑"),
                List.of("影视", "电影", "电视剧", "纪录片", "影视解说", "混剪", "剪辑", "娱乐", "搞笑"),
                List.of("解说", "混剪", "盘点", "幕后"),
                List.of("名场面", "反转", "催泪")));
        clusters.add(cluster("knowledge", "知识", categoryIdByName, tagIdByName,
                List.of("知识", "科普", "教程"),
                List.of("知识", "教程", "编程", "人工智能"),
                List.of("科普", "入门", "十分钟看懂", "方法论"),
                List.of("提升", "思维", "案例")));

        List<InterestCluster> usable = clusters.stream()
                .filter(cluster -> !cluster.categoryIds().isEmpty() && !cluster.tagIds().isEmpty())
                .toList();
        if (usable.isEmpty()) {
            throw new IllegalStateException("No usable seed clusters can be built from current category/tag data");
        }

        Map<String, InterestCluster> byKey = usable.stream()
                .collect(Collectors.toMap(InterestCluster::key, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return new SeedDomainSnapshot(
                usable,
                byKey,
                categories.stream().map(Category::getId).filter(Objects::nonNull).toList(),
                tags.stream().map(Tag::getId).filter(Objects::nonNull).toList()
        );
    }

    private InterestCluster cluster(
            String key,
            String displayName,
            Map<String, Long> categoryIdByName,
            Map<String, Long> tagIdByName,
            List<String> categoryNames,
            List<String> tagNames,
            List<String> titlePrefixes,
            List<String> titleKeywords
    ) {
        return new InterestCluster(
                key,
                displayName,
                ids(categoryNames, categoryIdByName),
                ids(tagNames, tagIdByName),
                titlePrefixes,
                titleKeywords
        );
    }

    private List<Long> ids(List<String> names, Map<String, Long> idByName) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String name : names) {
            Long id = idByName.get(name);
            if (id != null) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }
}
```

- [ ] **Step 4: 重新运行测试，确认兴趣域映射通过**

Run:

```bash
cd backend && mvn -Dtest=SeedDomainCatalogTest test
```

Expected:
- PASS
- `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: 提交这一小步**

```bash
git add backend/src/main/java/com/bilibili/video/seed/InterestCluster.java \
        backend/src/main/java/com/bilibili/video/seed/SeedDomainSnapshot.java \
        backend/src/main/java/com/bilibili/video/seed/SeedDomainCatalog.java \
        backend/src/test/java/com/bilibili/video/seed/SeedDomainCatalogTest.java

git commit -m "feat(seed): build interest domain catalog"
```

### Task 3: 生成作者和普通用户

**Files:**
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedUserPersona.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedAuthorProfile.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedUserProfile.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedPopulation.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedUserGenerator.java`
- Test: `backend/src/test/java/com/bilibili/video/seed/SeedUserGeneratorTest.java`

- [ ] **Step 1: 写失败测试，锁定用户数量、密码加密和 persona 分布**

```java
package com.bilibili.video.seed;

import com.bilibili.video.entity.User;
import com.bilibili.video.mapper.UserMapper;
import com.bilibili.video.utils.MyBCr;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class SeedUserGeneratorTest {

    @Test
    void shouldCreateExpectedCountsAndEncodedPasswords() {
        UserMapper userMapper = mock(UserMapper.class);
        AtomicLong ids = new AtomicLong(100);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(ids.incrementAndGet());
            return 1;
        }).when(userMapper).insert(any(User.class));

        SeedUserGenerator generator = new SeedUserGenerator(userMapper);
        SeedProfile profile = new SeedProfile(4, 12, 40, 100, 20, 10, 5,
                0.25D, 0.25D, 0.25D, 0.25D,
                0.70D, 0.20D, 0.10D, 30);
        SeedDomainSnapshot snapshot = new SeedDomainSnapshot(
                List.of(
                        new InterestCluster("gaming", "游戏", List.of(1L), List.of(11L), List.of("上分"), List.of("攻略")),
                        new InterestCluster("technology", "科技", List.of(2L), List.of(12L), List.of("实战"), List.of("项目"))
                ),
                java.util.Map.of(
                        "gaming", new InterestCluster("gaming", "游戏", List.of(1L), List.of(11L), List.of("上分"), List.of("攻略")),
                        "technology", new InterestCluster("technology", "科技", List.of(2L), List.of(12L), List.of("实战"), List.of("项目"))
                ),
                List.of(1L, 2L),
                List.of(11L, 12L)
        );

        SeedPopulation population = generator.generate(profile, snapshot, new Random(42L));

        assertThat(population.authors()).hasSize(4);
        assertThat(population.users()).hasSize(12);
        assertThat(population.users()).extracting(SeedUserProfile::persona)
                .contains(SeedUserPersona.HEAVY, SeedUserPersona.MEDIUM, SeedUserPersona.LIGHT, SeedUserPersona.COLD);
        assertThat(MyBCr.matches("SeedPass123!", population.rawUsers().get(0).getPassword())).isTrue();
        assertThat(population.rawUsers()).allMatch(user -> Boolean.FALSE.equals(user.getIsAdmin()));
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```bash
cd backend && mvn -Dtest=SeedUserGeneratorTest test
```

Expected:
- FAIL
- 缺少 `SeedUserGenerator` / `SeedPopulation` / `SeedUserPersona`

- [ ] **Step 3: 写最小实现，生成作者与普通用户并记录元数据**

`backend/src/main/java/com/bilibili/video/seed/SeedUserPersona.java`

```java
package com.bilibili.video.seed;

public enum SeedUserPersona {
    HEAVY,
    MEDIUM,
    LIGHT,
    COLD
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedAuthorProfile.java`

```java
package com.bilibili.video.seed;

public record SeedAuthorProfile(
        Long userId,
        String username,
        String tier,
        String clusterKey
) {
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedUserProfile.java`

```java
package com.bilibili.video.seed;

public record SeedUserProfile(
        Long userId,
        String username,
        SeedUserPersona persona,
        String primaryClusterKey,
        String secondaryClusterKey,
        double followBias,
        double explorationBias
) {
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedPopulation.java`

```java
package com.bilibili.video.seed;

import com.bilibili.video.entity.User;

import java.util.List;

public record SeedPopulation(
        List<SeedAuthorProfile> authors,
        List<SeedUserProfile> users,
        List<User> rawUsers
) {
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedUserGenerator.java`

```java
package com.bilibili.video.seed;

import com.bilibili.video.entity.User;
import com.bilibili.video.mapper.UserMapper;
import com.bilibili.video.utils.MyBCr;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class SeedUserGenerator {

    private static final String[] DEFAULT_AVATARS = {
            "default/微信图片_20260316115333.jpg",
            "default/微信图片_20260316115347.jpg",
            "default/微信图片_20260316115405.jpg",
            "default/微信图片_20260316115406.jpg",
            "default/微信图片_202603161154061.jpg",
            "default/微信图片_202603161154062.jpg"
    };

    private final UserMapper userMapper;

    public SeedPopulation generate(SeedProfile profile, SeedDomainSnapshot snapshot, Random random) {
        List<SeedAuthorProfile> authors = new ArrayList<>();
        List<SeedUserProfile> users = new ArrayList<>();
        List<User> rawUsers = new ArrayList<>();

        for (int i = 0; i < profile.authorCount(); i++) {
            InterestCluster cluster = snapshot.clusters().get(i % snapshot.clusters().size());
            User author = buildUser("up_" + cluster.key() + "_" + (1000 + i), random);
            userMapper.insert(author);
            rawUsers.add(author);
            authors.add(new SeedAuthorProfile(
                    author.getId(),
                    author.getUsername(),
                    i < Math.max(1, profile.authorCount() / 10) ? "HEAD" : i < Math.max(2, profile.authorCount() / 3) ? "MID" : "TAIL",
                    cluster.key()
            ));
        }

        int heavyLimit = (int) Math.round(profile.userCount() * profile.heavyUserRatio());
        int mediumLimit = heavyLimit + (int) Math.round(profile.userCount() * profile.mediumUserRatio());
        int lightLimit = mediumLimit + (int) Math.round(profile.userCount() * profile.lightUserRatio());

        for (int i = 0; i < profile.userCount(); i++) {
            InterestCluster primary = snapshot.clusters().get(random.nextInt(snapshot.clusters().size()));
            InterestCluster secondary = snapshot.clusters().get(random.nextInt(snapshot.clusters().size()));
            while (secondary.key().equals(primary.key()) && snapshot.clusters().size() > 1) {
                secondary = snapshot.clusters().get(random.nextInt(snapshot.clusters().size()));
            }
            SeedUserPersona persona = i < heavyLimit ? SeedUserPersona.HEAVY
                    : i < mediumLimit ? SeedUserPersona.MEDIUM
                    : i < lightLimit ? SeedUserPersona.LIGHT
                    : SeedUserPersona.COLD;
            User user = buildUser(primary.key() + "_user_" + (2000 + i), random);
            userMapper.insert(user);
            rawUsers.add(user);
            users.add(new SeedUserProfile(
                    user.getId(),
                    user.getUsername(),
                    persona,
                    primary.key(),
                    secondary.key(),
                    persona == SeedUserPersona.HEAVY ? 0.18D : persona == SeedUserPersona.MEDIUM ? 0.10D : 0.04D,
                    persona == SeedUserPersona.COLD ? 0.02D : 0.08D
            ));
        }

        return new SeedPopulation(authors, users, rawUsers);
    }

    private User buildUser(String username, Random random) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(MyBCr.encode("SeedPass123!"));
        user.setAvatar(DEFAULT_AVATARS[random.nextInt(DEFAULT_AVATARS.length)]);
        user.setIsAdmin(false);
        user.setCreateTime(LocalDateTime.now().minusDays(random.nextInt(120)));
        return user;
    }
}
```

- [ ] **Step 4: 重新运行测试，确认用户生成通过**

Run:

```bash
cd backend && mvn -Dtest=SeedUserGeneratorTest test
```

Expected:
- PASS
- `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 5: 提交这一小步**

```bash
git add backend/src/main/java/com/bilibili/video/seed/SeedUserPersona.java \
        backend/src/main/java/com/bilibili/video/seed/SeedAuthorProfile.java \
        backend/src/main/java/com/bilibili/video/seed/SeedUserProfile.java \
        backend/src/main/java/com/bilibili/video/seed/SeedPopulation.java \
        backend/src/main/java/com/bilibili/video/seed/SeedUserGenerator.java \
        backend/src/test/java/com/bilibili/video/seed/SeedUserGeneratorTest.java

git commit -m "feat(seed): generate seed users and authors"
```

### Task 4: 生成视频、标签关系和基础特征

**Files:**
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedVideoProfile.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedVideoGenerator.java`
- Test: `backend/src/test/java/com/bilibili/video/seed/SeedVideoGeneratorTest.java`

- [ ] **Step 1: 写失败测试，锁定视频数量、标签数量和特征同步行为**

```java
package com.bilibili.video.seed;

import com.bilibili.video.entity.Video;
import com.bilibili.video.entity.VideoTag;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.VideoTagMapper;
import com.bilibili.video.service.RecommendationFeatureService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SeedVideoGeneratorTest {

    @Test
    void shouldCreateVideosWithTwoToFourTagsAndSyncFeatures() {
        VideoMapper videoMapper = mock(VideoMapper.class);
        VideoTagMapper videoTagMapper = mock(VideoTagMapper.class);
        RecommendationFeatureService featureService = mock(RecommendationFeatureService.class);
        AtomicLong ids = new AtomicLong(10);
        doAnswer(invocation -> {
            Video video = invocation.getArgument(0);
            video.setId(ids.incrementAndGet());
            return 1;
        }).when(videoMapper).insert(any(Video.class));

        SeedVideoGenerator generator = new SeedVideoGenerator(videoMapper, videoTagMapper, featureService);
        SeedProfile profile = new SeedProfile(2, 4, 12, 100, 20, 10, 5,
                0.10D, 0.30D, 0.45D, 0.15D,
                0.70D, 0.20D, 0.10D, 30);
        SeedDomainSnapshot snapshot = new SeedDomainSnapshot(
                List.of(new InterestCluster("technology", "科技", List.of(5L), List.of(21L, 22L, 23L), List.of("实战"), List.of("项目"))),
                java.util.Map.of("technology", new InterestCluster("technology", "科技", List.of(5L), List.of(21L, 22L, 23L), List.of("实战"), List.of("项目"))),
                List.of(5L),
                List.of(21L, 22L, 23L)
        );
        SeedPopulation population = new SeedPopulation(
                List.of(
                        new SeedAuthorProfile(101L, "up_tech_1", "HEAD", "technology"),
                        new SeedAuthorProfile(102L, "up_tech_2", "TAIL", "technology")
                ),
                List.of(),
                List.of()
        );

        List<SeedVideoProfile> videos = generator.generate(profile, snapshot, population, new Random(42L));

        assertThat(videos).hasSize(12);
        assertThat(videos).allSatisfy(video -> assertThat(video.tagIds()).hasSizeBetween(2, 4));
        verify(featureService, times(12)).syncVideoTagFeatures(any(Long.class), anyList(), eq("seed"), eq("v1"), any());
        verify(videoTagMapper, times(videos.stream().mapToInt(video -> video.tagIds().size()).sum())).insert(any(VideoTag.class));
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```bash
cd backend && mvn -Dtest=SeedVideoGeneratorTest test
```

Expected:
- FAIL
- 缺少 `SeedVideoGenerator` / `SeedVideoProfile`

- [ ] **Step 3: 写最小实现，生成视频、video_tag 和 video_tag_feature**

`backend/src/main/java/com/bilibili/video/seed/SeedVideoProfile.java`

```java
package com.bilibili.video.seed;

import java.time.LocalDateTime;
import java.util.List;

public record SeedVideoProfile(
        Long videoId,
        Long authorId,
        String clusterKey,
        Long categoryId,
        List<Long> tagIds,
        boolean editorial,
        double potentialScore,
        LocalDateTime publishedAt
) {
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedVideoGenerator.java`

```java
package com.bilibili.video.seed;

import com.bilibili.video.entity.Video;
import com.bilibili.video.entity.VideoTag;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.VideoTagMapper;
import com.bilibili.video.service.RecommendationFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SeedVideoGenerator {

    private final VideoMapper videoMapper;
    private final VideoTagMapper videoTagMapper;
    private final RecommendationFeatureService recommendationFeatureService;

    public List<SeedVideoProfile> generate(SeedProfile profile, SeedDomainSnapshot snapshot, SeedPopulation population, Random random) {
        List<SeedVideoProfile> result = new ArrayList<>();
        int authorIndex = 0;
        for (int i = 0; i < profile.videoCount(); i++) {
            SeedAuthorProfile author = population.authors().get(authorIndex % population.authors().size());
            authorIndex++;
            InterestCluster cluster = snapshot.requireCluster(author.clusterKey());
            List<Long> tagIds = pickTags(cluster, random);
            LocalDateTime publishedAt = LocalDateTime.now()
                    .minusDays(random.nextInt(Math.max(1, profile.recentDaysWindow())))
                    .minusHours(random.nextInt(24));
            Video video = new Video();
            video.setTitle(buildTitle(cluster, i, random));
            video.setDescription(cluster.displayName() + " 向推荐测试视频 #" + i);
            video.setAuthorId(author.userId());
            video.setCoverUrl("seed://cover/" + author.userId() + "/" + i + ".png");
            video.setPreviewUrl("seed://preview/" + author.userId() + "/" + i + ".gif");
            video.setVideoUrl("seed://video/" + author.userId() + "/" + i + ".mp4");
            video.setPlayCount(0L);
            video.setLikeCount(0L);
            video.setSaveCount(0L);
            video.setDurationSeconds(60 + random.nextInt(1800));
            video.setIsRecommended(random.nextDouble() < 0.03D);
            video.setCategoryId(cluster.categoryIds().get(random.nextInt(cluster.categoryIds().size())));
            video.setCreateTime(publishedAt);
            videoMapper.insert(video);

            for (Long tagId : tagIds) {
                VideoTag relation = new VideoTag();
                relation.setVideoId(video.getId());
                relation.setTagId(tagId);
                relation.setCreateTime(publishedAt);
                videoTagMapper.insert(relation);
            }
            recommendationFeatureService.syncVideoTagFeatures(
                    video.getId(),
                    tagIds,
                    "seed",
                    "v1",
                    buildConfidenceMap(tagIds)
            );
            result.add(new SeedVideoProfile(
                    video.getId(),
                    author.userId(),
                    cluster.key(),
                    video.getCategoryId(),
                    tagIds,
                    Boolean.TRUE.equals(video.getIsRecommended()),
                    author.tier().equals("HEAD") ? 1.0D : author.tier().equals("MID") ? 0.65D : 0.35D,
                    publishedAt
            ));
        }
        return result;
    }

    private String buildTitle(InterestCluster cluster, int index, Random random) {
        String prefix = cluster.titlePrefixes().get(random.nextInt(cluster.titlePrefixes().size()));
        String keyword = cluster.titleKeywords().get(random.nextInt(cluster.titleKeywords().size()));
        return prefix + " · " + keyword + " 第" + (index + 1) + "期";
    }

    private List<Long> pickTags(InterestCluster cluster, Random random) {
        Set<Long> picked = new LinkedHashSet<>();
        int count = 2 + random.nextInt(Math.min(3, cluster.tagIds().size() - 1) + 1);
        while (picked.size() < count) {
            picked.add(cluster.tagIds().get(random.nextInt(cluster.tagIds().size())));
        }
        return List.copyOf(picked);
    }

    private Map<Long, Double> buildConfidenceMap(List<Long> tagIds) {
        Map<Long, Double> values = new LinkedHashMap<>();
        double current = 0.95D;
        for (Long tagId : tagIds) {
            values.put(tagId, current);
            current = Math.max(0.70D, current - 0.08D);
        }
        return values;
    }
}
```

- [ ] **Step 4: 重新运行测试，确认视频生成通过**

Run:

```bash
cd backend && mvn -Dtest=SeedVideoGeneratorTest test
```

Expected:
- PASS
- `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 5: 提交这一小步**

```bash
git add backend/src/main/java/com/bilibili/video/seed/SeedVideoProfile.java \
        backend/src/main/java/com/bilibili/video/seed/SeedVideoGenerator.java \
        backend/src/test/java/com/bilibili/video/seed/SeedVideoGeneratorTest.java

git commit -m "feat(seed): generate seed videos and tags"
```

### Task 5: 生成观看/点赞/收藏/关注行为

**Files:**
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedBehaviorResult.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedBehaviorGenerator.java`
- Test: `backend/src/test/java/com/bilibili/video/seed/SeedBehaviorGeneratorTest.java`

- [ ] **Step 1: 写失败测试，锁定行为量、persona 差异与兴趣偏向**

```java
package com.bilibili.video.seed;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SeedBehaviorGeneratorTest {

    @Test
    void shouldGenerateMoreWatchesForHeavyUsersThanColdUsers() {
        SeedBehaviorGenerator generator = new SeedBehaviorGenerator();
        SeedProfile profile = new SeedProfile(2, 4, 12, 120, 20, 10, 5,
                0.25D, 0.25D, 0.25D, 0.25D,
                0.70D, 0.20D, 0.10D, 30);
        SeedPopulation population = new SeedPopulation(
                List.of(
                        new SeedAuthorProfile(101L, "up_gaming", "HEAD", "gaming"),
                        new SeedAuthorProfile(102L, "up_tech", "TAIL", "technology")
                ),
                List.of(
                        new SeedUserProfile(201L, "heavy", SeedUserPersona.HEAVY, "gaming", "technology", 0.18D, 0.08D),
                        new SeedUserProfile(202L, "medium", SeedUserPersona.MEDIUM, "gaming", "technology", 0.10D, 0.08D),
                        new SeedUserProfile(203L, "light", SeedUserPersona.LIGHT, "technology", "gaming", 0.04D, 0.08D),
                        new SeedUserProfile(204L, "cold", SeedUserPersona.COLD, "technology", "gaming", 0.02D, 0.02D)
                ),
                List.of()
        );
        SeedDomainSnapshot snapshot = new SeedDomainSnapshot(
                List.of(
                        new InterestCluster("gaming", "游戏", List.of(1L), List.of(11L, 12L), List.of("上分"), List.of("攻略")),
                        new InterestCluster("technology", "科技", List.of(2L), List.of(21L, 22L), List.of("实战"), List.of("项目"))
                ),
                Map.of(
                        "gaming", new InterestCluster("gaming", "游戏", List.of(1L), List.of(11L, 12L), List.of("上分"), List.of("攻略")),
                        "technology", new InterestCluster("technology", "科技", List.of(2L), List.of(21L, 22L), List.of("实战"), List.of("项目"))
                ),
                List.of(1L, 2L),
                List.of(11L, 12L, 21L, 22L)
        );
        List<SeedVideoProfile> videos = List.of(
                new SeedVideoProfile(301L, 101L, "gaming", 1L, List.of(11L, 12L), false, 1.0D, LocalDateTime.now().minusDays(1)),
                new SeedVideoProfile(302L, 101L, "gaming", 1L, List.of(11L), false, 0.8D, LocalDateTime.now().minusDays(2)),
                new SeedVideoProfile(303L, 102L, "technology", 2L, List.of(21L, 22L), false, 0.7D, LocalDateTime.now().minusDays(1)),
                new SeedVideoProfile(304L, 102L, "technology", 2L, List.of(21L), true, 0.6D, LocalDateTime.now().minusDays(3))
        );

        SeedBehaviorResult result = generator.generate(profile, snapshot, population, videos, new Random(42L));

        long heavyWatchCount = result.watches().stream().filter(item -> item.getUserId().equals(201L)).count();
        long coldWatchCount = result.watches().stream().filter(item -> item.getUserId().equals(204L)).count();
        assertThat(heavyWatchCount).isGreaterThan(coldWatchCount);
        assertThat(result.playCountByVideo()).isNotEmpty();
        assertThat(result.userInterestWeights()).containsKey(201L);
        assertThat(result.follows().stream().map(item -> item.getFollowerId() + ":" + item.getFollowingId()).distinct().count())
                .isEqualTo(result.follows().size());
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```bash
cd backend && mvn -Dtest=SeedBehaviorGeneratorTest test
```

Expected:
- FAIL
- 缺少 `SeedBehaviorGenerator` / `SeedBehaviorResult`

- [ ] **Step 3: 写最小实现，基于兴趣与活跃度生成行为并聚合统计**

`backend/src/main/java/com/bilibili/video/seed/SeedBehaviorResult.java`

```java
package com.bilibili.video.seed;

import com.bilibili.video.entity.Favorite;
import com.bilibili.video.entity.Follow;
import com.bilibili.video.entity.VideoLike;
import com.bilibili.video.entity.WatchHistory;

import java.util.List;
import java.util.Map;

public record SeedBehaviorResult(
        List<WatchHistory> watches,
        List<VideoLike> likes,
        List<Favorite> favorites,
        List<Follow> follows,
        Map<Long, Long> playCountByVideo,
        Map<Long, Long> likeCountByVideo,
        Map<Long, Long> favoriteCountByVideo,
        Map<Long, Map<Long, Double>> userInterestWeights
) {
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedBehaviorGenerator.java`

```java
package com.bilibili.video.seed;

import com.bilibili.video.entity.Favorite;
import com.bilibili.video.entity.Follow;
import com.bilibili.video.entity.VideoLike;
import com.bilibili.video.entity.WatchHistory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SeedBehaviorGenerator {

    public SeedBehaviorResult generate(
            SeedProfile profile,
            SeedDomainSnapshot snapshot,
            SeedPopulation population,
            List<SeedVideoProfile> videos,
            Random random
    ) {
        Map<String, List<SeedVideoProfile>> videosByCluster = videos.stream()
                .collect(Collectors.groupingBy(SeedVideoProfile::clusterKey, LinkedHashMap::new, Collectors.toList()));
        List<WatchHistory> watches = new ArrayList<>();
        List<VideoLike> likes = new ArrayList<>();
        List<Favorite> favorites = new ArrayList<>();
        List<Follow> follows = new ArrayList<>();
        Map<Long, Long> playCountByVideo = new LinkedHashMap<>();
        Map<Long, Long> likeCountByVideo = new LinkedHashMap<>();
        Map<Long, Long> favoriteCountByVideo = new LinkedHashMap<>();
        Map<Long, Map<Long, Double>> userInterestWeights = new LinkedHashMap<>();
        Set<String> likePairs = new LinkedHashSet<>();
        Set<String> favoritePairs = new LinkedHashSet<>();
        Set<String> followPairs = new LinkedHashSet<>();

        for (SeedUserProfile user : population.users()) {
            int targetViews = switch (user.persona()) {
                case HEAVY -> 120;
                case MEDIUM -> 70;
                case LIGHT -> 28;
                case COLD -> 4;
            };
            targetViews += random.nextInt(8);
            for (int i = 0; i < targetViews; i++) {
                SeedVideoProfile picked = pickVideo(user, snapshot, videosByCluster, videos, profile, random);
                LocalDateTime watchedAt = picked.publishedAt().plusHours(1 + random.nextInt(240));
                WatchHistory watch = new WatchHistory();
                watch.setUserId(user.userId());
                watch.setVideoId(picked.videoId());
                watch.setWatchSeconds(20 + random.nextInt(600));
                watch.setCreateTime(watchedAt);
                watch.setUpdateTime(watchedAt);
                watches.add(watch);
                playCountByVideo.merge(picked.videoId(), 1L, Long::sum);

                Map<Long, Double> weights = userInterestWeights.computeIfAbsent(user.userId(), ignored -> new LinkedHashMap<>());
                double delta = user.persona() == SeedUserPersona.HEAVY ? 1.8D : user.persona() == SeedUserPersona.MEDIUM ? 1.2D : 0.6D;
                for (Long tagId : picked.tagIds()) {
                    weights.merge(tagId, delta, Double::sum);
                }

                if (random.nextDouble() < likeRate(user)) {
                    String key = user.userId() + ":" + picked.videoId();
                    if (likePairs.add(key)) {
                        VideoLike like = new VideoLike();
                        like.setUserId(user.userId());
                        like.setVideoId(picked.videoId());
                        like.setCreateTime(watchedAt.plusMinutes(1));
                        likes.add(like);
                        likeCountByVideo.merge(picked.videoId(), 1L, Long::sum);
                    }
                }
                if (random.nextDouble() < favoriteRate(user)) {
                    String key = user.userId() + ":" + picked.videoId();
                    if (favoritePairs.add(key)) {
                        Favorite favorite = new Favorite();
                        favorite.setUserId(user.userId());
                        favorite.setVideoId(picked.videoId());
                        favorite.setCreateTime(watchedAt.plusMinutes(2));
                        favorites.add(favorite);
                        favoriteCountByVideo.merge(picked.videoId(), 1L, Long::sum);
                    }
                }
                if (random.nextDouble() < user.followBias()) {
                    String key = user.userId() + ":" + picked.authorId();
                    if (!user.userId().equals(picked.authorId()) && followPairs.add(key)) {
                        Follow follow = new Follow();
                        follow.setFollowerId(user.userId());
                        follow.setFollowingId(picked.authorId());
                        follow.setCreateTime(watchedAt.plusMinutes(3));
                        follows.add(follow);
                    }
                }
            }
        }

        return new SeedBehaviorResult(watches, likes, favorites, follows,
                playCountByVideo, likeCountByVideo, favoriteCountByVideo, userInterestWeights);
    }

    private SeedVideoProfile pickVideo(
            SeedUserProfile user,
            SeedDomainSnapshot snapshot,
            Map<String, List<SeedVideoProfile>> videosByCluster,
            List<SeedVideoProfile> allVideos,
            SeedProfile profile,
            Random random
    ) {
        double draw = random.nextDouble();
        String clusterKey;
        if (draw < profile.primaryPreferenceWeight()) {
            clusterKey = user.primaryClusterKey();
        } else if (draw < profile.primaryPreferenceWeight() + profile.secondaryPreferenceWeight()) {
            clusterKey = user.secondaryClusterKey();
        } else {
            clusterKey = snapshot.clusters().get(random.nextInt(snapshot.clusters().size())).key();
        }
        List<SeedVideoProfile> candidates = videosByCluster.getOrDefault(clusterKey, allVideos);
        SeedVideoProfile candidate = candidates.get(random.nextInt(candidates.size()));
        if (random.nextDouble() < candidate.potentialScore() * 0.35D) {
            return candidate;
        }
        return allVideos.get(random.nextInt(allVideos.size()));
    }

    private double likeRate(SeedUserProfile user) {
        return switch (user.persona()) {
            case HEAVY -> 0.22D;
            case MEDIUM -> 0.14D;
            case LIGHT -> 0.08D;
            case COLD -> 0.03D;
        };
    }

    private double favoriteRate(SeedUserProfile user) {
        return switch (user.persona()) {
            case HEAVY -> 0.09D;
            case MEDIUM -> 0.05D;
            case LIGHT -> 0.02D;
            case COLD -> 0.005D;
        };
    }
}
```

- [ ] **Step 4: 重新运行测试，确认行为生成通过**

Run:

```bash
cd backend && mvn -Dtest=SeedBehaviorGeneratorTest test
```

Expected:
- PASS
- `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 5: 提交这一小步**

```bash
git add backend/src/main/java/com/bilibili/video/seed/SeedBehaviorResult.java \
        backend/src/main/java/com/bilibili/video/seed/SeedBehaviorGenerator.java \
        backend/src/test/java/com/bilibili/video/seed/SeedBehaviorGeneratorTest.java

git commit -m "feat(seed): generate realistic seed behaviors"
```

### Task 6: 回填推荐数据、热榜，并接上显式启动入口

**Files:**
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedProjectionService.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedGenerationSummary.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedOrchestrator.java`
- Create: `backend/src/main/java/com/bilibili/video/seed/SeedCommandRunner.java`
- Test: `backend/src/test/java/com/bilibili/video/seed/SeedProjectionServiceTest.java`
- Test: `backend/src/test/java/com/bilibili/video/seed/SeedCommandRunnerTest.java`

- [ ] **Step 1: 写失败测试，锁定投影回填、Redis 热榜写入和显式触发条件**

`backend/src/test/java/com/bilibili/video/seed/SeedProjectionServiceTest.java`

```java
package com.bilibili.video.seed;

import com.bilibili.video.common.Constants;
import com.bilibili.video.entity.UserInterestTag;
import com.bilibili.video.entity.Video;
import com.bilibili.video.mapper.FavoriteMapper;
import com.bilibili.video.mapper.FollowMapper;
import com.bilibili.video.mapper.UserInterestTagMapper;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.WatchHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeedProjectionServiceTest {

    private WatchHistoryMapper watchHistoryMapper;
    private VideoLikeMapper videoLikeMapper;
    private FavoriteMapper favoriteMapper;
    private FollowMapper followMapper;
    private UserInterestTagMapper userInterestTagMapper;
    private VideoMapper videoMapper;
    private RedisTemplate<String, Object> redisTemplate;
    private ZSetOperations<String, Object> zSetOperations;
    private SeedProjectionService service;

    @BeforeEach
    void setUp() {
        watchHistoryMapper = mock(WatchHistoryMapper.class);
        videoLikeMapper = mock(VideoLikeMapper.class);
        favoriteMapper = mock(FavoriteMapper.class);
        followMapper = mock(FollowMapper.class);
        userInterestTagMapper = mock(UserInterestTagMapper.class);
        videoMapper = mock(VideoMapper.class);
        redisTemplate = mock(RedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        service = new SeedProjectionService(watchHistoryMapper, videoLikeMapper, favoriteMapper, followMapper,
                userInterestTagMapper, videoMapper, redisTemplate);
    }

    @Test
    void shouldPersistVideoStatsAndHotRank() {
        SeedBehaviorResult result = new SeedBehaviorResult(
                List.of(), List.of(), List.of(), List.of(),
                Map.of(11L, 30L),
                Map.of(11L, 6L),
                Map.of(11L, 4L),
                Map.of(21L, Map.of(301L, 4.5D, 302L, 2.5D))
        );

        service.persist(result);

        ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
        verify(videoMapper).updateById(videoCaptor.capture());
        assertThat(videoCaptor.getValue().getId()).isEqualTo(11L);
        assertThat(videoCaptor.getValue().getPlayCount()).isEqualTo(30L);
        assertThat(videoCaptor.getValue().getLikeCount()).isEqualTo(6L);
        assertThat(videoCaptor.getValue().getSaveCount()).isEqualTo(4L);
        verify(zSetOperations).add(anyString(), any(), org.mockito.eq(30D * Constants.HOT_WEIGHT_PLAY + 6D * Constants.HOT_WEIGHT_LIKE + 4D * Constants.HOT_WEIGHT_FAVORITE));
        verify(userInterestTagMapper, times(2)).insert(any(UserInterestTag.class));
    }
}
```

`backend/src/test/java/com/bilibili/video/seed/SeedCommandRunnerTest.java`

```java
package com.bilibili.video.seed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SeedCommandRunnerTest {

    @Test
    void shouldSkipWhenSeedDisabled() throws Exception {
        SeedProperties properties = new SeedProperties();
        properties.setEnabled(false);
        SeedOrchestrator orchestrator = mock(SeedOrchestrator.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

        new SeedCommandRunner(properties, orchestrator, context).run(new DefaultApplicationArguments(new String[0]));

        verifyNoInteractions(orchestrator);
        verifyNoInteractions(context);
    }

    @Test
    void shouldRunAndCloseContextWhenSeedEnabled() throws Exception {
        SeedProperties properties = new SeedProperties();
        properties.setEnabled(true);
        properties.setAppend(true);
        SeedOrchestrator orchestrator = mock(SeedOrchestrator.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

        new SeedCommandRunner(properties, orchestrator, context).run(new DefaultApplicationArguments(new String[0]));

        verify(orchestrator).run();
        verify(context).close();
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```bash
cd backend && mvn -Dtest=SeedProjectionServiceTest,SeedCommandRunnerTest test
```

Expected:
- FAIL
- 缺少 `SeedProjectionService` / `SeedOrchestrator` / `SeedCommandRunner`

- [ ] **Step 3: 写最小实现，持久化行为、回填画像与热榜，并通过 runner 显式启动**

`backend/src/main/java/com/bilibili/video/seed/SeedProjectionService.java`

```java
package com.bilibili.video.seed;

import com.bilibili.video.common.Constants;
import com.bilibili.video.entity.Favorite;
import com.bilibili.video.entity.Follow;
import com.bilibili.video.entity.UserInterestTag;
import com.bilibili.video.entity.Video;
import com.bilibili.video.entity.VideoLike;
import com.bilibili.video.entity.WatchHistory;
import com.bilibili.video.mapper.FavoriteMapper;
import com.bilibili.video.mapper.FollowMapper;
import com.bilibili.video.mapper.UserInterestTagMapper;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.WatchHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SeedProjectionService {

    private final WatchHistoryMapper watchHistoryMapper;
    private final VideoLikeMapper videoLikeMapper;
    private final FavoriteMapper favoriteMapper;
    private final FollowMapper followMapper;
    private final UserInterestTagMapper userInterestTagMapper;
    private final VideoMapper videoMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public void persist(SeedBehaviorResult result) {
        for (WatchHistory watch : result.watches()) {
            watchHistoryMapper.insert(watch);
        }
        for (VideoLike like : result.likes()) {
            videoLikeMapper.insert(like);
        }
        for (Favorite favorite : result.favorites()) {
            favoriteMapper.insert(favorite);
        }
        for (Follow follow : result.follows()) {
            followMapper.insert(follow);
        }
        persistUserInterestWeights(result.userInterestWeights());
        persistVideoStatsAndHotRank(result);
    }

    private void persistUserInterestWeights(Map<Long, Map<Long, Double>> weightsByUser) {
        for (Map.Entry<Long, Map<Long, Double>> userEntry : weightsByUser.entrySet()) {
            for (Map.Entry<Long, Double> tagEntry : userEntry.getValue().entrySet()) {
                UserInterestTag row = new UserInterestTag();
                row.setUserId(userEntry.getKey());
                row.setTagId(tagEntry.getKey());
                row.setWeight(tagEntry.getValue());
                userInterestTagMapper.insert(row);
            }
        }
    }

    private void persistVideoStatsAndHotRank(SeedBehaviorResult result) {
        String key = Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
        for (Map.Entry<Long, Long> entry : result.playCountByVideo().entrySet()) {
            Long videoId = entry.getKey();
            long playCount = entry.getValue();
            long likeCount = result.likeCountByVideo().getOrDefault(videoId, 0L);
            long favoriteCount = result.favoriteCountByVideo().getOrDefault(videoId, 0L);
            Video video = new Video();
            video.setId(videoId);
            video.setPlayCount(playCount);
            video.setLikeCount(likeCount);
            video.setSaveCount(favoriteCount);
            videoMapper.updateById(video);
            double hotScore = playCount * Constants.HOT_WEIGHT_PLAY
                    + likeCount * Constants.HOT_WEIGHT_LIKE
                    + favoriteCount * Constants.HOT_WEIGHT_FAVORITE;
            redisTemplate.opsForZSet().add(key, String.valueOf(videoId), hotScore);
        }
        redisTemplate.expire(key, Constants.HOT_WINDOW_HOURS, TimeUnit.HOURS);
    }
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedGenerationSummary.java`

```java
package com.bilibili.video.seed;

public record SeedGenerationSummary(
        int authors,
        int users,
        int videos,
        int watches,
        int likes,
        int favorites,
        int follows,
        boolean searchReindexed
) {
    public String toLogLine() {
        return "authors=" + authors + ", users=" + users + ", videos=" + videos
                + ", watches=" + watches + ", likes=" + likes + ", favorites=" + favorites
                + ", follows=" + follows + ", searchReindexed=" + searchReindexed;
    }
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedOrchestrator.java`

```java
package com.bilibili.video.seed;

import com.bilibili.video.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class SeedOrchestrator {

    private final SeedProperties properties;
    private final SeedProfileCatalog profileCatalog;
    private final SeedDomainCatalog domainCatalog;
    private final SeedUserGenerator userGenerator;
    private final SeedVideoGenerator videoGenerator;
    private final SeedBehaviorGenerator behaviorGenerator;
    private final SeedProjectionService projectionService;
    private final SearchService searchService;

    public SeedGenerationSummary run() {
        properties.validateOrThrow();
        SeedProfile profile = profileCatalog.resolve(properties);
        SeedDomainSnapshot snapshot = domainCatalog.load();
        Random random = new Random(properties.getRandomSeed() == null ? 42L : properties.getRandomSeed());
        SeedPopulation population = userGenerator.generate(profile, snapshot, random);
        List<SeedVideoProfile> videos = videoGenerator.generate(profile, snapshot, population, random);
        SeedBehaviorResult result = behaviorGenerator.generate(profile, snapshot, population, videos, random);
        projectionService.persist(result);
        boolean searchReindexed = false;
        if (properties.isSearchReindex()) {
            searchService.reindexAllVideos();
            searchReindexed = true;
        }
        return new SeedGenerationSummary(
                population.authors().size(),
                population.users().size(),
                videos.size(),
                result.watches().size(),
                result.likes().size(),
                result.favorites().size(),
                result.follows().size(),
                searchReindexed
        );
    }
}
```

`backend/src/main/java/com/bilibili/video/seed/SeedCommandRunner.java`

```java
package com.bilibili.video.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeedCommandRunner implements ApplicationRunner {

    private final SeedProperties properties;
    private final SeedOrchestrator orchestrator;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        SeedGenerationSummary summary = orchestrator.run();
        log.warn("Seed generation complete: {}", summary.toLogLine());
        context.close();
    }
}
```

- [ ] **Step 4: 重新运行测试，确认投影与 runner 通过**

Run:

```bash
cd backend && mvn -Dtest=SeedProjectionServiceTest,SeedCommandRunnerTest test
```

Expected:
- PASS
- `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: 跑一轮种子相关单测集合，确认前面所有任务可一起工作**

Run:

```bash
cd backend && mvn -Dtest=SeedProfileCatalogTest,SeedDomainCatalogTest,SeedUserGeneratorTest,SeedVideoGeneratorTest,SeedBehaviorGeneratorTest,SeedProjectionServiceTest,SeedCommandRunnerTest test
```

Expected:
- PASS
- 所有 seed 测试均通过

- [ ] **Step 6: 提交这一小步**

```bash
git add backend/src/main/java/com/bilibili/video/seed/SeedProjectionService.java \
        backend/src/main/java/com/bilibili/video/seed/SeedGenerationSummary.java \
        backend/src/main/java/com/bilibili/video/seed/SeedOrchestrator.java \
        backend/src/main/java/com/bilibili/video/seed/SeedCommandRunner.java \
        backend/src/test/java/com/bilibili/video/seed/SeedProjectionServiceTest.java \
        backend/src/test/java/com/bilibili/video/seed/SeedCommandRunnerTest.java

git commit -m "feat(seed): wire seed orchestration and runner"
```

### Task 7: 文档、手动验证与最终回归

**Files:**
- Modify: `README.md`
- Modify: `backend/README.md`

- [ ] **Step 1: 先写文档更新内容，让命令和依赖边界清晰可见**

在 `README.md` 的“完整功能模式”或“推荐系统”相关章节追加以下片段：

```md
## 推荐测试数据生成

如果你想验证推荐算法、冷启动、热门召回和缓存行为，可以直接运行 backend 内置 seed 命令：

```bash
cd backend
mvn spring-boot:run "-Dspring-boot.run.arguments=--seed.enabled=true --seed.mode=medium --seed.append=true --seed.search-reindex=false --seed.random-seed=42"
```

说明：
- 该命令只会在显式传入 `--seed.enabled=true` 时执行
- 当前版本只支持 `append=true`，不会清空现有业务数据
- 默认生成中等规模用户、视频与行为数据
- 如果本地 Elasticsearch 可用，可以把 `--seed.search-reindex=false` 改成 `true`
- 生成的视频 URL 为 seed 占位值，第一版不包含真实媒体资源
```

在 `backend/README.md` 追加以下片段：

```md
## Seed Generator

用于本地追加推荐测试数据：

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--seed.enabled=true --seed.mode=small --seed.append=true --seed.search-reindex=false --seed.random-seed=42"
```

推荐调试顺序：
1. 先用 `small` 验证流程
2. 再切到 `medium` 观察推荐与缓存表现
3. 仅在本地资源足够时再尝试更大的 override 数量

运行完成后，应用会输出作者、用户、视频、观看、点赞、收藏、关注数量摘要并自动退出。
```

- [ ] **Step 2: 运行完整测试回归，确认文档更新前代码仍然全绿**

Run:

```bash
cd backend && mvn test
```

Expected:
- PASS
- 现有测试 + 新增 seed 测试全部通过

- [ ] **Step 3: 运行一次 small 模式命令，验证一条命令可完成追加和退出**

Run:

```bash
cd backend && mvn spring-boot:run "-Dspring-boot.run.arguments=--seed.enabled=true --seed.mode=small --seed.append=true --seed.search-reindex=false --seed.random-seed=42"
```

Expected:
- 启动日志中出现 `Seed generation complete:`
- 进程在输出摘要后自动退出
- 不要求 MinIO / ffmpeg / RocketMQ 参与该流程

- [ ] **Step 4: 用数据库和 Redis 做结构验证，确认结果真实可用**

Run:

```bash
mysql -u root -p123456 -e "use bilibili; select count(*) as users from user; select count(*) as videos from video; select count(*) as watches from watch_history; select count(*) as likes from video_like; select count(*) as favorites from favorite; select count(*) as follows from follow;"
redis-cli ZREVRANGE video:hot:24h 0 9 WITHSCORES
```

Expected:
- `user` / `video` / `watch_history` / `video_like` / `favorite` / `follow` 数量都有增加
- `video:hot:24h` 至少返回 10 条视频 ID 和 score

- [ ] **Step 5: 用推荐接口做功能验证，确认数据能驱动推荐结果变化**

Run:

```bash
curl "http://localhost:8080/video/recommend?page=1&size=10"
```

Expected:
- 返回 10 条推荐视频
- 不是空列表
- 热门/新鲜/多样性结果能从返回内容中观察到明显差异

- [ ] **Step 6: 提交文档与验证结论**

```bash
git add README.md backend/README.md

git commit -m "docs: add seed generator usage guide"
```

---

## Spec coverage check

- 单命令执行：Task 6 的 `SeedCommandRunner` 完成显式触发与自动退出。
- 追加式写入：Task 1 的 `append=true` 安全校验 + 所有 generator 只执行 insert，不清理现有数据。
- 真实分布：Task 2 的兴趣域、Task 3 的 persona 与 Task 5 的行为权重共同实现。
- 推荐相关投影：Task 4 写 `video_tag_feature`，Task 6 写 `user_interest_tag`、视频统计和 Redis 热榜。
- 可选搜索重建：Task 6 通过 `SearchService.reindexAllVideos()` 受 `seed.searchReindex` 控制。
- 文档与验证：Task 7 更新 README 并给出 DB/Redis/API 验证命令。

## Placeholder scan

- 没有 `TODO` / `TBD` / “implement later” 之类占位词。
- 每个任务都给出了测试代码、实现代码、运行命令和预期结果。
- 后续任务引用的类型都已在前面任务定义：`SeedProfile`、`InterestCluster`、`SeedPopulation`、`SeedVideoProfile`、`SeedBehaviorResult`。

## Type consistency check

- 配置入口统一使用 `SeedProperties` + `SeedProfileCatalog`。
- 领域快照统一使用 `SeedDomainSnapshot`。
- 用户/作者集合统一使用 `SeedPopulation`。
- 视频集合统一使用 `SeedVideoProfile`。
- 行为与统计统一使用 `SeedBehaviorResult`。
- 任务之间未更换方法名或类型名。
