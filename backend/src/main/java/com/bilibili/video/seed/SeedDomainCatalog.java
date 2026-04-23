package com.bilibili.video.seed;

import com.bilibili.video.entity.Category;
import com.bilibili.video.entity.Tag;
import com.bilibili.video.mapper.CategoryMapper;
import com.bilibili.video.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class SeedDomainCatalog {

    private static final List<ClusterDefinition> CLUSTER_DEFINITIONS = List.of(
            clusterDefinition(
                    "animation", "动画",
                    List.of("动画", "二次元", "MAD/AMV", "鬼畜"),
                    List.of("二次元", "鬼畜", "剪辑"),
                    List.of("高能", "安利", "名场面"),
                    List.of("二次元", "剪辑", "混剪")
            ),
            clusterDefinition(
                    "gaming", "游戏",
                    List.of("游戏", "手游", "单机", "网游", "电竞"),
                    List.of("电竞", "攻略", "解说", "集锦"),
                    List.of("上分", "版本", "复盘"),
                    List.of("教学", "名场面", "对局")
            ),
            clusterDefinition(
                    "technology", "科技",
                    List.of("科技", "编程开发", "人工智能", "数码评测"),
                    List.of("编程开发", "Java", "Python", "Go", "TypeScript", "Node.js", "SpringBoot", "SpringCloud", "Vue", "React", "MySQL", "PostgreSQL", "MongoDB", "Redis", "Docker", "Linux", "Git", "Kafka", "Nginx", "Elasticsearch", "Netty", "Kubernetes", "Maven", "Gradle", "RabbitMQ", "DevOps", "CI/CD", "人工智能", "机器学习", "数码", "开箱", "评测", "教程", "实战", "面试", "项目讲解", "原理", "源码", "调优", "避坑"),
                    List.of("实战", "入门", "项目", "源码"),
                    List.of("教程", "面试", "拆解", "调优")
            ),
            clusterDefinition(
                    "knowledge", "知识",
                    List.of("知识", "科普", "学习方法", "人文社科"),
                    List.of("科普", "教程", "干货", "复盘", "心理学", "阅读", "书评", "职场", "成长", "效率"),
                    List.of("十分钟看懂", "方法论", "复盘"),
                    List.of("提升", "思维", "案例", "阅读")
            ),
            clusterDefinition(
                    "lifestyle", "生活",
                    List.of("生活", "日常", "校园", "健身", "穿搭"),
                    List.of("校园", "健身", "穿搭", "搞笑", "摄影", "家居", "宠物", "情感"),
                    List.of("日常", "周末", "记录"),
                    List.of("治愈", "成长", "分享", "陪伴")
            ),
            clusterDefinition(
                    "film", "影视",
                    List.of("影视", "电影", "电视剧", "纪录片"),
                    List.of("影视解说", "剧情", "剪辑", "解说", "混剪", "名场面", "高能", "催泪", "影评", "吐槽", "配音"),
                    List.of("盘点", "混剪", "幕后"),
                    List.of("名场面", "反转", "催泪", "高能")
            ),
            clusterDefinition(
                    "music", "音乐",
                    List.of("音乐", "翻唱", "演奏", "说唱"),
                    List.of("翻唱", "演奏", "说唱"),
                    List.of("翻唱", "现场", "纯享"),
                    List.of("循环", "改编", "练习")
            ),
            clusterDefinition(
                    "sports", "体育",
                    List.of("体育", "篮球", "足球", "综合体育"),
                    List.of("篮球", "足球", "集锦", "攻略"),
                    List.of("复盘", "高光", "战术"),
                    List.of("绝杀", "进球", "球星")
            ),
            clusterDefinition(
                    "food", "美食",
                    List.of("美食", "家常菜", "探店", "烘焙饮品"),
                    List.of("探店", "评测", "Vlog"),
                    List.of("打卡", "测评", "做饭"),
                    List.of("治愈", "下饭", "城市")
            ),
            clusterDefinition(
                    "vlog", "Vlog",
                    List.of("Vlog", "出行", "城市记录", "个人日常"),
                    List.of("Vlog", "剪辑", "搞笑"),
                    List.of("记录", "周末", "旅行"),
                    List.of("日常", "出行", "城市")
            )
    );

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

        List<InterestCluster> usable = CLUSTER_DEFINITIONS.stream()
                .map(definition -> cluster(definition, categoryIdByName, tagIdByName))
                .filter(Objects::nonNull)
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
            ClusterDefinition definition,
            Map<String, Long> categoryIdByName,
            Map<String, Long> tagIdByName
    ) {
        List<Long> categoryIds = ids(definition.categoryNames(), categoryIdByName);
        List<Long> tagIds = ids(definition.tagNames(), tagIdByName);
        if (categoryIds.isEmpty()) {
            log.warn("Skipping seed cluster [{}] because no category ids were resolved from {}", definition.key(), definition.categoryNames());
        }
        if (tagIds.isEmpty()) {
            log.warn("Skipping seed cluster [{}] because no tag ids were resolved from {}", definition.key(), definition.tagNames());
        }
        if (categoryIds.isEmpty() || tagIds.isEmpty()) {
            return null;
        }
        return new InterestCluster(
                definition.key(),
                definition.displayName(),
                categoryIds,
                tagIds,
                definition.titlePrefixes(),
                definition.titleKeywords()
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

    private static ClusterDefinition clusterDefinition(
            String key,
            String displayName,
            List<String> categoryNames,
            List<String> tagNames,
            List<String> titlePrefixes,
            List<String> titleKeywords
    ) {
        return new ClusterDefinition(key, displayName, categoryNames, tagNames, titlePrefixes, titleKeywords);
    }

    private record ClusterDefinition(
            String key,
            String displayName,
            List<String> categoryNames,
            List<String> tagNames,
            List<String> titlePrefixes,
            List<String> titleKeywords
    ) {
    }
}