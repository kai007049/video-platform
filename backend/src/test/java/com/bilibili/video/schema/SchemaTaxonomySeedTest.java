package com.bilibili.video.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

class SchemaTaxonomySeedTest {

    private static final List<CategorySeed> EXPECTED_CATEGORIES = List.of(
            new CategorySeed("动画", 0),
            new CategorySeed("游戏", 0),
            new CategorySeed("科技", 0),
            new CategorySeed("知识", 0),
            new CategorySeed("生活", 0),
            new CategorySeed("影视", 0),
            new CategorySeed("音乐", 0),
            new CategorySeed("体育", 0),
            new CategorySeed("美食", 0),
            new CategorySeed("Vlog", 0),
            new CategorySeed("二次元", 1),
            new CategorySeed("MAD/AMV", 1),
            new CategorySeed("鬼畜", 1),
            new CategorySeed("手游", 2),
            new CategorySeed("单机", 2),
            new CategorySeed("网游", 2),
            new CategorySeed("电竞", 2),
            new CategorySeed("编程开发", 3),
            new CategorySeed("人工智能", 3),
            new CategorySeed("数码评测", 3),
            new CategorySeed("科普", 4),
            new CategorySeed("学习方法", 4),
            new CategorySeed("人文社科", 4),
            new CategorySeed("日常", 5),
            new CategorySeed("校园", 5),
            new CategorySeed("健身", 5),
            new CategorySeed("穿搭", 5),
            new CategorySeed("电影", 6),
            new CategorySeed("电视剧", 6),
            new CategorySeed("纪录片", 6),
            new CategorySeed("翻唱", 7),
            new CategorySeed("演奏", 7),
            new CategorySeed("说唱", 7),
            new CategorySeed("篮球", 8),
            new CategorySeed("足球", 8),
            new CategorySeed("综合体育", 8),
            new CategorySeed("家常菜", 9),
            new CategorySeed("探店", 9),
            new CategorySeed("烘焙饮品", 9),
            new CategorySeed("出行", 10),
            new CategorySeed("城市记录", 10),
            new CategorySeed("个人日常", 10)
    );

    private static final List<String> EXPECTED_TAGS = List.of(
            "编程开发", "人工智能", "数码", "电竞", "校园", "健身", "穿搭", "探店", "财经", "心理学", "科普", "二次元",
            "阅读", "书评", "职场", "成长", "效率", "摄影", "家居", "宠物", "情感", "旅行",
            "教程", "实战", "入门", "进阶", "评测", "解说", "干货", "面试", "项目讲解", "复盘", "开箱", "剪辑", "Vlog", "搞笑",
            "原理", "源码", "调优", "避坑", "盘点", "高光", "上手", "版本解析", "开荒",
            "Java", "Python", "Go", "TypeScript", "Node.js", "SpringBoot", "SpringCloud", "Vue", "React", "MySQL", "PostgreSQL", "MongoDB", "Redis", "Docker", "Linux", "Git", "Kafka", "Nginx", "Elasticsearch", "Netty", "Kubernetes", "Maven", "Gradle", "RabbitMQ", "DevOps", "CI/CD",
            "前端", "后端", "算法", "机器学习", "系统设计", "高并发", "微服务", "分布式", "数据结构", "计算机网络", "操作系统",
            "鬼畜", "翻唱", "影视解说", "篮球", "足球", "剧情", "集锦", "攻略", "混剪", "名场面", "高能", "催泪", "影评", "吐槽", "配音"
    );

    private static final Pattern CATEGORY_INSERT_BLOCK = Pattern.compile(
            "INSERT INTO category \\(name, parent_id\\) VALUES\\s*(.*?);",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern CATEGORY_VALUE = Pattern.compile("\\('([^']+)',\\s*(\\d+)\\)");

    private static final Pattern TAG_INSERT_BLOCK = Pattern.compile(
            "INSERT INTO tag \\(name\\) VALUES\\s*(.*?);",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern TAG_VALUE = Pattern.compile("\\('([^']+)'\\)");

    private static final Map<String, Integer> EXPECTED_NEW_ROOT_CATEGORIES = Map.ofEntries(
            entry("动画", 0),
            entry("科技", 0),
            entry("知识", 0),
            entry("美食", 0),
            entry("Vlog", 0)
    );

    private static final Map<String, Integer> EXPECTED_NEW_TECH_CHILDREN = Map.ofEntries(
            entry("编程开发", 3),
            entry("人工智能", 3),
            entry("数码评测", 3)
    );

    private static final List<String> REMOVED_LEGACY_CATEGORIES = List.of(
            "娱乐",
            "旅行",
            "数码",
            "编程",
            "前端",
            "后端",
            "机器学习",
            "教程"
    );

    @Test
    void shouldKeepNewCategorySeedsInSchemaSql() throws IOException {
        List<CategorySeed> schemaCategories = extractCategorySeed(Path.of("src/main/resources/db/schema.sql"));

        assertThat(schemaCategories).containsAll(EXPECTED_CATEGORIES);
        assertThat(schemaCategories)
                .containsAll(entriesToCategorySeeds(EXPECTED_NEW_ROOT_CATEGORIES))
                .containsAll(entriesToCategorySeeds(EXPECTED_NEW_TECH_CHILDREN));
        assertThat(schemaCategories)
                .extracting(CategorySeed::name)
                .doesNotContainAnyElementsOf(REMOVED_LEGACY_CATEGORIES);
    }

    @Test
    void shouldKeepCategorySeedsStrictlyAlignedAcrossSchemaAndResetSql() throws IOException {
        List<CategorySeed> schemaCategories = extractCategorySeed(Path.of("src/main/resources/db/schema.sql"));
        List<CategorySeed> resetCategories = extractCategorySeed(Path.of("src/main/resources/db/dev_reset_taxonomy.sql"));

        assertThat(schemaCategories).containsExactlyElementsOf(EXPECTED_CATEGORIES);
        assertThat(resetCategories).containsExactlyElementsOf(EXPECTED_CATEGORIES);
        assertThat(resetCategories).containsExactlyElementsOf(schemaCategories);
        assertThat(schemaCategories)
                .extracting(CategorySeed::name)
                .doesNotContainAnyElementsOf(REMOVED_LEGACY_CATEGORIES);
        assertThat(resetCategories)
                .extracting(CategorySeed::name)
                .doesNotContainAnyElementsOf(REMOVED_LEGACY_CATEGORIES);
    }

    @Test
    void shouldKeepTagSeedsStrictlyAlignedWithFiveSpecGroupsInBothSqlFiles() throws IOException {
        List<String> schemaTags = extractTagSeed(Path.of("src/main/resources/db/schema.sql"));
        List<String> resetTags = extractTagSeed(Path.of("src/main/resources/db/dev_reset_taxonomy.sql"));

        assertThat(schemaTags).containsExactlyElementsOf(EXPECTED_TAGS);
        assertThat(resetTags).containsExactlyElementsOf(EXPECTED_TAGS);
        assertThat(resetTags).containsExactlyElementsOf(schemaTags);
        assertThat(schemaTags).doesNotContain("电影", "电视剧", "纪录片");
        assertThat(resetTags).doesNotContain("电影", "电视剧", "纪录片");
    }

    private static List<String> extractTagSeed(Path path) throws IOException {
        String sql = Files.readString(path);
        Matcher blockMatcher = TAG_INSERT_BLOCK.matcher(sql);
        assertThat(blockMatcher.find())
                .as("tag insert block should exist in %s", path)
                .isTrue();

        Matcher valueMatcher = TAG_VALUE.matcher(blockMatcher.group(1));
        List<String> tags = valueMatcher.results()
                .map(result -> result.group(1))
                .toList();

        assertThat(tags)
                .as("tag seed rows should be parsed from %s", path)
                .isNotEmpty();
        return tags;
    }

    private static List<CategorySeed> extractCategorySeed(Path path) throws IOException {
        String sql = Files.readString(path);
        Matcher blockMatcher = CATEGORY_INSERT_BLOCK.matcher(sql);
        assertThat(blockMatcher.find())
                .as("category insert block should exist in %s", path)
                .isTrue();

        Matcher valueMatcher = CATEGORY_VALUE.matcher(blockMatcher.group(1));
        List<CategorySeed> categories = valueMatcher.results()
                .map(result -> new CategorySeed(result.group(1), Integer.parseInt(result.group(2))))
                .toList();

        assertThat(categories)
                .as("category seed rows should be parsed from %s", path)
                .isNotEmpty();
        return categories;
    }

    private static List<CategorySeed> entriesToCategorySeeds(Map<String, Integer> categories) {
        return categories.entrySet().stream()
                .map(entry -> new CategorySeed(entry.getKey(), entry.getValue()))
                .toList();
    }

    private record CategorySeed(String name, int parentId) {
    }
}