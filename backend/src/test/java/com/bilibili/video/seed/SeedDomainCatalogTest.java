package com.bilibili.video.seed;

import com.bilibili.video.entity.Category;
import com.bilibili.video.entity.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedDomainCatalogTest {

    @Test
    void shouldBuildUsableTechnologyFoodAndVlogClustersUsingNewTaxonomy() {
        SeedDomainCatalog catalog = new SeedDomainCatalog(null, null);
        List<Category> categories = List.of(
                category(1L, "动画"), category(11L, "二次元"), category(12L, "MAD/AMV"),
                category(2L, "游戏"), category(14L, "手游"), category(17L, "电竞"),
                category(3L, "科技"), category(18L, "编程开发"), category(19L, "人工智能"), category(20L, "数码评测"),
                category(4L, "知识"), category(21L, "科普"),
                category(5L, "生活"), category(24L, "日常"),
                category(6L, "影视"), category(28L, "电影"),
                category(7L, "音乐"), category(31L, "翻唱"),
                category(8L, "体育"), category(34L, "篮球"),
                category(9L, "美食"), category(37L, "家常菜"), category(38L, "探店"), category(39L, "烘焙饮品"),
                category(10L, "Vlog"), category(40L, "出行"), category(41L, "城市记录"), category(42L, "个人日常")
        );
        List<Tag> tags = List.of(
                tag(1L, "编程开发"), tag(2L, "Java"), tag(3L, "SpringBoot"), tag(4L, "Vue"), tag(5L, "React"),
                tag(6L, "MySQL"), tag(7L, "Redis"), tag(8L, "Docker"), tag(9L, "Linux"), tag(10L, "Git"),
                tag(11L, "Kafka"), tag(12L, "Nginx"), tag(13L, "Elasticsearch"), tag(14L, "Netty"), tag(15L, "人工智能"),
                tag(16L, "机器学习"), tag(17L, "数码"), tag(18L, "开箱"), tag(19L, "评测"), tag(20L, "教程"),
                tag(21L, "实战"), tag(22L, "面试"), tag(23L, "项目讲解"), tag(24L, "探店"), tag(25L, "Vlog"),
                tag(26L, "剪辑"), tag(27L, "鬼畜"), tag(28L, "二次元"), tag(29L, "翻唱"), tag(30L, "篮球")
        );

        SeedDomainSnapshot snapshot = catalog.buildSnapshot(categories, tags);
        Map<Long, String> categoryNameById = categories.stream().collect(Collectors.toMap(Category::getId, Category::getName));
        Map<Long, String> tagNameById = tags.stream().collect(Collectors.toMap(Tag::getId, Tag::getName));

        assertCluster(snapshot.requireCluster("technology"), categoryNameById, tagNameById,
                "technology", "科技",
                List.of(3L, 18L, 19L, 20L),
                List.of("科技", "编程开发", "人工智能", "数码评测"),
                List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L, 22L, 23L),
                List.of("编程开发", "Java", "SpringBoot", "Vue", "React", "MySQL", "Redis", "Docker", "Linux", "Git", "Kafka", "Nginx", "Elasticsearch", "Netty", "人工智能", "机器学习", "数码", "开箱", "评测", "教程", "实战", "面试", "项目讲解"),
                List.of("实战", "入门", "项目", "源码"),
                List.of("教程", "面试", "拆解", "调优")
        );

        InterestCluster technology = snapshot.requireCluster("technology");
        assertThat(resolveNames(technology.categoryIds(), categoryNameById)).doesNotContain("编程", "前端", "后端");

        assertCluster(snapshot.requireCluster("food"), categoryNameById, tagNameById,
                "food", "美食",
                List.of(9L, 37L, 38L, 39L),
                List.of("美食", "家常菜", "探店", "烘焙饮品"),
                List.of(24L, 19L, 25L),
                List.of("探店", "评测", "Vlog"),
                List.of("打卡", "测评", "做饭"),
                List.of("治愈", "下饭", "城市")
        );

        assertCluster(snapshot.requireCluster("vlog"), categoryNameById, tagNameById,
                "vlog", "Vlog",
                List.of(10L, 40L, 41L, 42L),
                List.of("Vlog", "出行", "城市记录", "个人日常"),
                List.of(25L, 26L),
                List.of("Vlog", "剪辑"),
                List.of("记录", "周末", "旅行"),
                List.of("日常", "出行", "城市")
        );
    }

    @Test
    void shouldFilterOutClusterWhenOnlyCategoriesMatchButTagsDoNot() {
        SeedDomainCatalog catalog = new SeedDomainCatalog(null, null);

        SeedDomainSnapshot snapshot = catalog.buildSnapshot(
                List.of(
                        category(3L, "科技"),
                        category(18L, "编程开发"),
                        category(19L, "人工智能"),
                        category(20L, "数码评测"),
                        category(9L, "美食"),
                        category(37L, "家常菜"),
                        category(38L, "探店"),
                        category(39L, "烘焙饮品")
                ),
                List.of(
                        tag(11L, "鬼畜"),
                        tag(24L, "探店"),
                        tag(25L, "Vlog")
                )
        );

        assertThat(snapshot.clusters()).extracting(InterestCluster::key)
                .contains("food")
                .doesNotContain("technology");
        assertThatThrownBy(() -> snapshot.requireCluster("technology"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown cluster: technology");
    }

    @Test
    void shouldThrowWhenRequireClusterUsesUnknownKey() {
        SeedDomainSnapshot snapshot = new SeedDomainSnapshot(List.of(), java.util.Map.of(), List.of(), List.of());

        assertThatThrownBy(() -> snapshot.requireCluster("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown cluster: unknown");
    }

    @Test
    void shouldFailWhenNoClusterCanBeBuilt() {
        SeedDomainCatalog catalog = new SeedDomainCatalog(null, null);

        assertThatThrownBy(() -> catalog.buildSnapshot(List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No usable seed clusters");
    }

    private void assertCluster(
            InterestCluster cluster,
            Map<Long, String> categoryNameById,
            Map<Long, String> tagNameById,
            String key,
            String displayName,
            List<Long> categoryIds,
            List<String> categoryNames,
            List<Long> tagIds,
            List<String> tagNames,
            List<String> titlePrefixes,
            List<String> titleKeywords
    ) {
        assertThat(cluster.key()).isEqualTo(key);
        assertThat(cluster.displayName()).isEqualTo(displayName);
        assertThat(cluster.categoryIds()).containsExactlyElementsOf(categoryIds);
        assertThat(resolveNames(cluster.categoryIds(), categoryNameById)).containsExactlyElementsOf(categoryNames);
        assertThat(cluster.tagIds()).containsExactlyElementsOf(tagIds);
        assertThat(resolveNames(cluster.tagIds(), tagNameById)).containsExactlyElementsOf(tagNames);
        assertThat(cluster.titlePrefixes()).containsExactlyElementsOf(titlePrefixes);
        assertThat(cluster.titleKeywords()).containsExactlyElementsOf(titleKeywords);
    }

    private List<String> resolveNames(List<Long> ids, Map<Long, String> namesById) {
        return ids.stream().map(namesById::get).toList();
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