# Category / Tag Taxonomy Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用一套新的 category / tag taxonomy 替换当前混乱的初始化数据，并同步更新后端分类推荐逻辑、seed 目录和前端上传页标签配置。

**Architecture:** 后端以 `schema.sql` 和一份开发环境重置 SQL 为基线重建 taxonomy 数据，同时把所有依赖分类/标签名称的业务逻辑（`LocalContentAnalysisService`、`SeedDomainCatalog`）改到新体系上。前端不改上传页主流程，只更新 `uploadTagConfig.js` 和对应测试，让标签分组和“分类 → 推荐标签”映射与新 taxonomy 保持一致。

**Tech Stack:** Spring Boot 3、MyBatis-Plus、MySQL、Redis、Vue 3、Vite、JUnit 5、Mockito、node:test

---

## File Structure

### Backend files
- Modify: `backend/src/main/resources/db/schema.sql:301-434`
  - 替换 category / tag 初始化数据为新的 taxonomy
- Create: `backend/src/main/resources/db/dev_reset_taxonomy.sql`
  - 开发环境清空旧 taxonomy 相关数据并插入新 taxonomy
- Modify: `backend/src/main/java/com/bilibili/video/service/impl/LocalContentAnalysisService.java:98-213`
  - 把分类推荐从旧 taxonomy 迁到新 taxonomy
- Create: `backend/src/test/java/com/bilibili/video/service/impl/LocalContentAnalysisServiceTest.java`
  - 用 TDD 锁定新 taxonomy 下的分类推荐行为
- Modify: `backend/src/main/java/com/bilibili/video/seed/SeedDomainCatalog.java:31-127`
  - seed 聚类目录切换到新 taxonomy 命名
- Create: `backend/src/test/java/com/bilibili/video/seed/SeedDomainCatalogTest.java`
  - 验证新 taxonomy 能构建可用 cluster
- Create: `backend/src/test/java/com/bilibili/video/schema/SchemaTaxonomySeedTest.java`
  - 验证 `schema.sql` 中已替换为新 taxonomy，且旧根分类被移除

### Frontend files
- Modify: `frontend/src/views/uploadTagConfig.js:1-39`
  - 按新 taxonomy 重写标签分组、默认标签、分类 → 推荐标签映射
- Modify: `frontend/tests/uploadTagCategoryState.test.js:1-180`
  - 让现有 helper 测试切换到新 taxonomy 词汇和映射

### Reference files
- `docs/superpowers/specs/2026-04-19-category-tag-taxonomy-rebuild-design.md`
- `backend/src/main/java/com/bilibili/video/seed/InterestCluster.java`
- `backend/src/main/java/com/bilibili/video/seed/SeedDomainSnapshot.java`
- `frontend/src/views/uploadTagCategoryState.js`

---

### Task 1: 更新 LocalContentAnalysisService 的分类推荐规则

**Files:**
- Create: `backend/src/test/java/com/bilibili/video/service/impl/LocalContentAnalysisServiceTest.java`
- Modify: `backend/src/main/java/com/bilibili/video/service/impl/LocalContentAnalysisService.java:98-213`

- [ ] **Step 1: Write the failing test**

```java
package com.bilibili.video.service.impl;

import com.bilibili.video.client.dto.ContentAnalysisResult;
import com.bilibili.video.entity.Category;
import com.bilibili.video.entity.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalContentAnalysisServiceTest {

    private final LocalContentAnalysisService service = new LocalContentAnalysisService();

    @Test
    void shouldSuggestProgrammingDevelopmentForJavaContent() {
        ContentAnalysisResult result = service.analyzeContent(
                "Java SpringBoot 项目实战",
                "后端接口开发与 Redis 缓存整合",
                List.of(tag(1L, "Java"), tag(2L, "SpringBoot"), tag(3L, "Redis"), tag(4L, "教程"), tag(5L, "实战")),
                List.of(
                        category(3L, "科技", 0L),
                        category(18L, "编程开发", 3L),
                        category(19L, "人工智能", 3L),
                        category(20L, "数码评测", 3L)
                )
        );

        assertThat(result.getSuggestedCategoryName()).isEqualTo("编程开发");
    }

    @Test
    void shouldSuggestAiCategoryForMachineLearningContent() {
        ContentAnalysisResult result = service.analyzeContent(
                "机器学习入门",
                "人工智能实战案例与算法讲解",
                List.of(tag(11L, "人工智能"), tag(12L, "机器学习"), tag(13L, "算法"), tag(14L, "教程")),
                List.of(
                        category(3L, "科技", 0L),
                        category(18L, "编程开发", 3L),
                        category(19L, "人工智能", 3L)
                )
        );

        assertThat(result.getSuggestedCategoryName()).isEqualTo("人工智能");
    }

    @Test
    void shouldSuggestDigitalReviewForUnboxingContent() {
        ContentAnalysisResult result = service.analyzeContent(
                "新手机开箱评测",
                "数码开箱与性能评测",
                List.of(tag(21L, "数码"), tag(22L, "开箱"), tag(23L, "评测")),
                List.of(
                        category(3L, "科技", 0L),
                        category(20L, "数码评测", 3L)
                )
        );

        assertThat(result.getSuggestedCategoryName()).isEqualTo("数码评测");
    }

    private Category category(Long id, String name, Long parentId) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setParentId(parentId);
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

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
cd backend && mvn -Dtest=LocalContentAnalysisServiceTest test
```

Expected:
- FAIL
- 至少 `shouldSuggestProgrammingDevelopmentForJavaContent` 会失败，当前实现更可能返回 `科技` 或 `知识`

- [ ] **Step 3: Write minimal implementation**

```java
private Long resolveCategoryIdByTags(List<Tag> matchedTags, List<Category> allCategories) {
    if (matchedTags == null || matchedTags.isEmpty()) {
        return null;
    }
    for (Tag tag : matchedTags) {
        if (tag == null || isBlank(tag.getName())) {
            continue;
        }
        String name = normalize(tag.getName());
        if (List.of("java", "springboot", "springcloud", "vue", "react", "mysql", "redis", "docker", "linux", "git", "kafka", "nginx", "elasticsearch", "netty").contains(name)) {
            return findCategoryIdByPreferredNames(allCategories, List.of("编程开发", "科技"));
        }
        if (List.of("人工智能", "机器学习", "算法").stream().map(this::normalize).toList().contains(name)) {
            return findCategoryIdByPreferredNames(allCategories, List.of("人工智能", "科技"));
        }
        if (List.of("数码", "开箱", "评测", "测评").stream().map(this::normalize).toList().contains(name)) {
            return findCategoryIdByPreferredNames(allCategories, List.of("数码评测", "科技"));
        }
        if (List.of("科普").stream().map(this::normalize).toList().contains(name)) {
            return findCategoryIdByPreferredNames(allCategories, List.of("科普", "知识"));
        }
    }
    return null;
}

private Long resolveCategoryIdByText(String source, List<Category> allCategories) {
    if (isBlank(source)) {
        return null;
    }
    if (containsAny(source, List.of("java", "springboot", "springcloud", "vue", "react", "mysql", "redis", "docker", "linux", "git", "kafka", "nginx", "elasticsearch", "netty"))) {
        return findCategoryIdByPreferredNames(allCategories, List.of("编程开发", "科技"));
    }
    if (containsAny(source, List.of("人工智能", "机器学习", "算法"))) {
        return findCategoryIdByPreferredNames(allCategories, List.of("人工智能", "科技"));
    }
    if (containsAny(source, List.of("数码", "开箱", "评测", "测评"))) {
        return findCategoryIdByPreferredNames(allCategories, List.of("数码评测", "科技"));
    }
    if (containsAny(source, List.of("科普", "学习方法"))) {
        return findCategoryIdByPreferredNames(allCategories, List.of("科普", "学习方法", "知识"));
    }
    return null;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
cd backend && mvn -Dtest=LocalContentAnalysisServiceTest test
```

Expected:
- PASS
- `LocalContentAnalysisServiceTest` 三个测试全部通过

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/bilibili/video/service/impl/LocalContentAnalysisService.java backend/src/test/java/com/bilibili/video/service/impl/LocalContentAnalysisServiceTest.java
git commit -m "feat: align local content analysis with new taxonomy"
```

---

### Task 2: 更新 SeedDomainCatalog 到新 taxonomy

**Files:**
- Create: `backend/src/test/java/com/bilibili/video/seed/SeedDomainCatalogTest.java`
- Modify: `backend/src/main/java/com/bilibili/video/seed/SeedDomainCatalog.java:31-127`

- [ ] **Step 1: Write the failing test**

```java
package com.bilibili.video.seed;

import com.bilibili.video.entity.Category;
import com.bilibili.video.entity.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeedDomainCatalogTest {

    @Test
    void shouldBuildClustersUsingNewTaxonomyNames() {
        SeedDomainCatalog catalog = new SeedDomainCatalog(null, null);
        SeedDomainSnapshot snapshot = catalog.buildSnapshot(
                List.of(
                        category(1L, "动画"), category(11L, "二次元"), category(12L, "MAD/AMV"),
                        category(2L, "游戏"), category(14L, "手游"), category(17L, "电竞"),
                        category(3L, "科技"), category(18L, "编程开发"), category(19L, "人工智能"), category(20L, "数码评测"),
                        category(4L, "知识"), category(21L, "科普"),
                        category(5L, "生活"), category(24L, "日常"),
                        category(6L, "影视"), category(28L, "电影"),
                        category(7L, "音乐"), category(31L, "翻唱"),
                        category(8L, "体育"), category(34L, "篮球"),
                        category(9L, "美食"), category(37L, "家常菜"),
                        category(10L, "Vlog"), category(40L, "出行")
                ),
                List.of(
                        tag(1L, "编程开发"), tag(2L, "Java"), tag(3L, "SpringBoot"), tag(4L, "教程"), tag(5L, "实战"),
                        tag(6L, "人工智能"), tag(7L, "机器学习"), tag(8L, "数码"), tag(9L, "开箱"), tag(10L, "评测"),
                        tag(11L, "鬼畜"), tag(12L, "二次元"), tag(13L, "翻唱"), tag(14L, "篮球"), tag(15L, "家常菜"), tag(16L, "Vlog")
                )
        );

        assertThat(snapshot.requireCluster("technology").categoryIds()).contains(3L, 18L, 19L, 20L);
        assertThat(snapshot.requireCluster("technology").tagIds()).contains(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(snapshot.requireCluster("food").categoryIds()).contains(9L, 37L);
        assertThat(snapshot.requireCluster("vlog").categoryIds()).contains(10L, 40L);
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

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
cd backend && mvn -Dtest=SeedDomainCatalogTest test
```

Expected:
- FAIL
- `technology` cluster 不会包含 `编程开发 / 人工智能 / 数码评测`
- `food` / `vlog` cluster 在当前实现中也不存在

- [ ] **Step 3: Write minimal implementation**

```java
List<InterestCluster> clusters = new ArrayList<>();
clusters.add(cluster("animation", "动画", categoryIdByName, tagIdByName,
        List.of("动画", "二次元", "MAD/AMV", "鬼畜"),
        List.of("二次元", "鬼畜", "剪辑"),
        List.of("高能", "安利", "名场面"),
        List.of("二次元", "剪辑", "混剪")));
clusters.add(cluster("gaming", "游戏", categoryIdByName, tagIdByName,
        List.of("游戏", "手游", "单机", "网游", "电竞"),
        List.of("电竞", "攻略", "解说", "集锦"),
        List.of("上分", "版本", "复盘"),
        List.of("教学", "名场面", "对局")));
clusters.add(cluster("technology", "科技", categoryIdByName, tagIdByName,
        List.of("科技", "编程开发", "人工智能", "数码评测"),
        List.of("编程开发", "Java", "SpringBoot", "Vue", "React", "MySQL", "Redis", "Docker", "Linux", "Git", "Kafka", "Nginx", "Elasticsearch", "Netty", "人工智能", "机器学习", "数码", "开箱", "评测", "教程", "实战", "面试", "项目讲解"),
        List.of("实战", "入门", "项目"),
        List.of("教程", "面试", "拆解")));
clusters.add(cluster("knowledge", "知识", categoryIdByName, tagIdByName,
        List.of("知识", "科普", "学习方法", "人文社科"),
        List.of("科普", "教程", "干货", "复盘", "心理学"),
        List.of("十分钟看懂", "方法论", "复盘"),
        List.of("提升", "思维", "案例")));
clusters.add(cluster("lifestyle", "生活", categoryIdByName, tagIdByName,
        List.of("生活", "日常", "校园", "健身", "穿搭"),
        List.of("校园", "健身", "穿搭", "搞笑"),
        List.of("日常", "周末", "记录"),
        List.of("治愈", "成长", "分享")));
clusters.add(cluster("film", "影视", categoryIdByName, tagIdByName,
        List.of("影视", "电影", "电视剧", "纪录片"),
        List.of("电影", "电视剧", "纪录片", "影视解说", "剧情", "剪辑", "解说"),
        List.of("盘点", "混剪", "幕后"),
        List.of("名场面", "反转", "催泪")));
clusters.add(cluster("music", "音乐", categoryIdByName, tagIdByName,
        List.of("音乐", "翻唱", "演奏", "说唱"),
        List.of("翻唱", "演奏", "说唱"),
        List.of("翻唱", "现场", "纯享"),
        List.of("循环", "改编", "练习")));
clusters.add(cluster("sports", "体育", categoryIdByName, tagIdByName,
        List.of("体育", "篮球", "足球", "综合体育"),
        List.of("篮球", "足球", "集锦", "攻略"),
        List.of("复盘", "高光", "战术"),
        List.of("绝杀", "进球", "球星")));
clusters.add(cluster("food", "美食", categoryIdByName, tagIdByName,
        List.of("美食", "家常菜", "探店", "烘焙饮品"),
        List.of("探店", "评测", "Vlog"),
        List.of("打卡", "测评", "做饭"),
        List.of("治愈", "下饭", "城市")));
clusters.add(cluster("vlog", "Vlog", categoryIdByName, tagIdByName,
        List.of("Vlog", "出行", "城市记录", "个人日常"),
        List.of("Vlog", "剪辑", "搞笑"),
        List.of("记录", "周末", "旅行"),
        List.of("日常", "出行", "城市")));
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
cd backend && mvn -Dtest=SeedDomainCatalogTest test
```

Expected:
- PASS
- technology / food / vlog cluster 都能正确解析 categoryIds 与 tagIds

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/bilibili/video/seed/SeedDomainCatalog.java backend/src/test/java/com/bilibili/video/seed/SeedDomainCatalogTest.java
git commit -m "feat: rebuild seed clusters for new taxonomy"
```

---

### Task 3: 重写 schema.sql taxonomy 数据并新增开发环境重置脚本

**Files:**
- Create: `backend/src/test/java/com/bilibili/video/schema/SchemaTaxonomySeedTest.java`
- Modify: `backend/src/main/resources/db/schema.sql:301-434`
- Create: `backend/src/main/resources/db/dev_reset_taxonomy.sql`

- [ ] **Step 1: Write the failing test**

```java
package com.bilibili.video.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaTaxonomySeedTest {

    @Test
    void shouldReplaceLegacyCategoryAndTagSeeds() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/schema.sql"));

        assertThat(sql).contains("('动画', 0)");
        assertThat(sql).contains("('科技', 0)");
        assertThat(sql).contains("('编程开发', 3)");
        assertThat(sql).contains("('人工智能', 3)");
        assertThat(sql).contains("('数码评测', 3)");
        assertThat(sql).contains("('Vlog', 0)");
        assertThat(sql).contains("('编程开发')");
        assertThat(sql).contains("('SpringCloud')");
        assertThat(sql).contains("('Elasticsearch')");
        assertThat(sql).doesNotContain("('娱乐', 0)");
        assertThat(sql).doesNotContain("('旅行', 0)");
        assertThat(sql).doesNotContain("('数码', 0)");
        assertThat(sql).doesNotContain("('编程', 5)");
        assertThat(sql).doesNotContain("('前端', 5)");
        assertThat(sql).doesNotContain("('后端', 5)");
        assertThat(sql).doesNotContain("('教程', 8)");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
cd backend && mvn -Dtest=SchemaTaxonomySeedTest test
```

Expected:
- FAIL
- 因为当前 `schema.sql` 还保留旧 taxonomy（娱乐 / 旅行 / 数码 / 编程 / 前端 / 后端 / 教程）

- [ ] **Step 3: Write minimal implementation**

```sql
-- backend/src/main/resources/db/dev_reset_taxonomy.sql
DELETE FROM user_interest_tag;
DELETE FROM video_tag_feature;
DELETE FROM video_tag;
UPDATE video SET category_id = 0;
DELETE FROM tag;
ALTER TABLE tag AUTO_INCREMENT = 1;
DELETE FROM category;
ALTER TABLE category AUTO_INCREMENT = 1;

INSERT INTO category (name, parent_id) VALUES
('动画', 0),
('游戏', 0),
('科技', 0),
('知识', 0),
('生活', 0),
('影视', 0),
('音乐', 0),
('体育', 0),
('美食', 0),
('Vlog', 0),
('二次元', 1),
('MAD/AMV', 1),
('鬼畜', 1),
('手游', 2),
('单机', 2),
('网游', 2),
('电竞', 2),
('编程开发', 3),
('人工智能', 3),
('数码评测', 3),
('科普', 4),
('学习方法', 4),
('人文社科', 4),
('日常', 5),
('校园', 5),
('健身', 5),
('穿搭', 5),
('电影', 6),
('电视剧', 6),
('纪录片', 6),
('翻唱', 7),
('演奏', 7),
('说唱', 7),
('篮球', 8),
('足球', 8),
('综合体育', 8),
('家常菜', 9),
('探店', 9),
('烘焙饮品', 9),
('出行', 10),
('城市记录', 10),
('个人日常', 10);

INSERT INTO tag (name) VALUES
('编程开发'),
('人工智能'),
('数码'),
('电竞'),
('校园'),
('健身'),
('穿搭'),
('探店'),
('财经'),
('心理学'),
('科普'),
('二次元'),
('教程'),
('实战'),
('入门'),
('进阶'),
('评测'),
('解说'),
('干货'),
('面试'),
('项目讲解'),
('复盘'),
('开箱'),
('剪辑'),
('Vlog'),
('搞笑'),
('Java'),
('SpringBoot'),
('SpringCloud'),
('Vue'),
('React'),
('MySQL'),
('Redis'),
('Docker'),
('Linux'),
('Git'),
('Kafka'),
('Nginx'),
('Elasticsearch'),
('Netty'),
('前端'),
('后端'),
('算法'),
('机器学习'),
('系统设计'),
('高并发'),
('微服务'),
('分布式'),
('数据结构'),
('计算机网络'),
('操作系统'),
('鬼畜'),
('翻唱'),
('影视解说'),
('电影'),
('电视剧'),
('纪录片'),
('篮球'),
('足球'),
('剧情'),
('集锦'),
('攻略');
```

同步把 `schema.sql` 中原有的 category / tag 初始化块替换为同样的数据集合。

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
cd backend && mvn -Dtest=SchemaTaxonomySeedTest test
```

Expected:
- PASS
- `schema.sql` 中只保留新 taxonomy 词汇，旧根分类与旧技术子分类已消失

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/schema.sql backend/src/main/resources/db/dev_reset_taxonomy.sql backend/src/test/java/com/bilibili/video/schema/SchemaTaxonomySeedTest.java
git commit -m "feat: rebuild category and tag seed taxonomy"
```

---

### Task 4: 同步前端 taxonomy 配置并更新 helper 测试

**Files:**
- Modify: `frontend/src/views/uploadTagConfig.js:1-39`
- Modify: `frontend/tests/uploadTagCategoryState.test.js:1-180`

- [ ] **Step 1: Write the failing test**

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  resolveCategorySelectionState,
  buildUploadTagSections
} from '../src/views/uploadTagCategoryState.js'

const categories = [
  {
    id: 3,
    name: '科技',
    children: [
      { id: 18, name: '编程开发' },
      { id: 19, name: '人工智能' },
      { id: 20, name: '数码评测' }
    ]
  },
  {
    id: 10,
    name: 'Vlog',
    children: [
      { id: 40, name: '出行' }
    ]
  }
]

const tags = [
  { id: 1, name: '编程开发' },
  { id: 2, name: 'Java' },
  { id: 3, name: 'SpringBoot' },
  { id: 4, name: '教程' },
  { id: 5, name: '实战' },
  { id: 6, name: '系统设计' },
  { id: 7, name: 'Vlog' },
  { id: 8, name: '校园' }
]

test('category state builds new taxonomy label for programming development', () => {
  const state = resolveCategorySelectionState({
    categories,
    selectedParentCategoryId: 3,
    categoryId: 18
  })

  assert.equal(state.selectedCategoryLabel, '科技 / 编程开发')
})

test('tag sections use new taxonomy mapping for programming development', () => {
  const sections = buildUploadTagSections({
    tags,
    selectedTagIds: [],
    suggestedTagIds: [],
    categoryLabel: '科技 / 编程开发',
    keyword: '',
    showAllTags: false
  })

  assert.deepEqual(
    sections.categoryTags.map(item => item.name),
    ['编程开发', 'Java', 'SpringBoot', '教程', '实战']
  )
})
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
cd frontend && node --test tests/uploadTagCategoryState.test.js
```

Expected:
- FAIL
- 旧配置只支持 `科技 / 后端`、`科技 / 前端` 这类映射，不会命中 `科技 / 编程开发`

- [ ] **Step 3: Write minimal implementation**

```js
// frontend/src/views/uploadTagConfig.js
export const uploadTagGroups = [
  {
    key: 'topic',
    label: '内容主题',
    tagNames: ['编程开发', '人工智能', '数码', '电竞', '校园', '健身', '穿搭', '探店', '财经', '心理学', '科普', '二次元']
  },
  {
    key: 'format',
    label: '内容形式',
    tagNames: ['教程', '实战', '入门', '进阶', '评测', '解说', '干货', '面试', '项目讲解', '复盘', '开箱', '剪辑', 'Vlog', '搞笑']
  },
  {
    key: 'tech',
    label: '技术 / 工具',
    tagNames: ['Java', 'SpringBoot', 'SpringCloud', 'Vue', 'React', 'MySQL', 'Redis', 'Docker', 'Linux', 'Git', 'Kafka', 'Nginx', 'Elasticsearch', 'Netty']
  },
  {
    key: 'direction',
    label: '方向 / 场景',
    tagNames: ['前端', '后端', '算法', '机器学习', '系统设计', '高并发', '微服务', '分布式', '数据结构', '计算机网络', '操作系统']
  },
  {
    key: 'style',
    label: '平台内容 / 风格',
    tagNames: ['鬼畜', '翻唱', '影视解说', '电影', '电视剧', '纪录片', '篮球', '足球', '剧情', '集锦', '攻略']
  }
]

export const defaultTagNames = ['教程', '实战', 'Vlog', '评测', '解说', '干货']

export const categoryTagMap = {
  '科技': ['编程开发', '人工智能', '数码', 'Java', 'SpringBoot', 'Vue', 'React', 'MySQL', 'Redis', 'Docker', '教程'],
  '科技 / 编程开发': ['编程开发', 'Java', 'SpringBoot', 'Vue', 'React', 'MySQL', 'Redis', 'Docker', 'Linux', 'Git', '前端', '后端', '数据结构', '计算机网络', '操作系统', '教程', '实战', '面试', '项目讲解'],
  '科技 / 人工智能': ['人工智能', '机器学习', '算法', '教程', '实战', '干货'],
  '科技 / 数码评测': ['数码', '开箱', '评测', '解说'],
  '知识': ['科普', '心理学', '干货', '复盘'],
  '知识 / 科普': ['科普', '干货', '解说', '复盘'],
  '影视 / 电影': ['电影', '剧情', '解说', '影视解说', '剪辑'],
  '游戏 / 电竞': ['电竞', '攻略', '解说', '集锦'],
  'Vlog': ['Vlog', '校园', '搞笑'],
  'Vlog / 出行': ['Vlog', '探店', '剪辑']
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
cd frontend && node --test tests/uploadTagCategoryState.test.js tests/uploadValidation.test.js
```

Expected:
- PASS
- helper 测试已切换到新 taxonomy 名称
- 现有上传校验测试无回归

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/uploadTagConfig.js frontend/tests/uploadTagCategoryState.test.js
git commit -m "feat: sync upload tag config with new taxonomy"
```

---

## Post-Implementation Verification

完成 4 个任务后，按下面顺序做开发环境重置和端到端验证：

1. 执行 taxonomy 重置 SQL

```bash
cd backend && mysql -uroot -p123456 bilibili < src/main/resources/db/dev_reset_taxonomy.sql
```

Expected:
- taxonomy 相关表被清空并重建
- `video.category_id` 被重置为 `0`

2. 清理 Redis taxonomy 缓存

```bash
redis-cli DEL category:tree category:list tag:list
```

Expected:
- 返回删除数量 >= 1，或者至少确认 key 不再存在

3. 运行后端测试

```bash
cd backend && mvn -Dtest=LocalContentAnalysisServiceTest,SeedDomainCatalogTest,SchemaTaxonomySeedTest test
```

Expected:
- PASS
- 3 个测试类全部通过

4. 启动后端

```bash
cd backend && mvn spring-boot:run
```

5. 运行前端测试

```bash
cd frontend && node --test tests/uploadValidation.test.js tests/uploadTagCategoryState.test.js
```

Expected:
- PASS

6. 启动前端

```bash
cd frontend && npm run dev
```

7. 手工验证上传页

Manual path:
```text
1. 打开 /upload
2. 点击“科技”后应出现：编程开发 / 人工智能 / 数码评测
3. 点击“科技 / 编程开发”后，标签区应优先出现：编程开发、Java、SpringBoot、教程、实战
4. 点击“Vlog”后应出现：出行 / 城市记录 / 个人日常
5. “更多标签”分组应显示为：内容主题 / 内容形式 / 技术 / 工具 / 方向 / 场景 / 平台内容 / 风格
```

Expected:
- 一级分类不再混入技术粒度项（前端/后端/Java）
- 上传页标签映射与新 taxonomy 一致
- `/category/tree` 返回的是规范树结构而不是平铺列表

---

## Spec Coverage Self-Review

- 已覆盖“删除旧 category/tag、完全重建”：Task 3
- 已覆盖“开发环境重置，不兼容旧 ID”：Task 3 + Post-Implementation Verification
- 已覆盖“分类只做内容领域”：Task 3 + Task 2
- 已覆盖“标签承担特征表达”：Task 3 + Task 4
- 已覆盖“分类 → 推荐标签映射”：Task 4
- 已覆盖“后端分类推荐逻辑切换到新 taxonomy”：Task 1
- 已覆盖“seed 目录与 taxonomy 同步”：Task 2
- 无 TBD / TODO / implement later / similar to 等占位内容
- 计划中 category / tag 名称已与 spec 保持一致，`编程开发 / 人工智能 / 数码评测 / 科普 / 学习方法 / 人文社科 / Vlog` 等新体系名称在各任务中一致

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-19-category-tag-taxonomy-rebuild.md`.

按你的要求，后续默认直接使用 **Subagent-Driven** 执行，不再额外询问执行方式。