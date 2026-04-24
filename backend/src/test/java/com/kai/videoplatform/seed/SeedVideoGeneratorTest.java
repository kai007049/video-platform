package com.kai.videoplatform.seed;

import com.kai.videoplatform.entity.Video;
import com.kai.videoplatform.entity.VideoTag;
import com.kai.videoplatform.mapper.VideoMapper;
import com.kai.videoplatform.mapper.VideoTagMapper;
import com.kai.videoplatform.service.RecommendationFeatureService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SeedVideoGeneratorTest {

    @Test
    void shouldCreateVideosWithReasonableMetadataAndSyncFeatures() {
        TestFixture fixture = new TestFixture();

        List<SeedVideoProfile> videos = fixture.generator.generate(
                fixture.profile,
                fixture.snapshot,
                fixture.populationWithTiers(
                        SeedAuthorProfile.AuthorTier.HEAD,
                        SeedAuthorProfile.AuthorTier.MID,
                        SeedAuthorProfile.AuthorTier.TAIL
                ),
                new Random(42L)
        );

        assertThat(videos).hasSize(12);
        assertThat(videos).allSatisfy(video -> {
            assertThat(video.authorId()).isNotNull();
            assertThat(video.clusterKey()).isEqualTo("technology");
            assertThat(video.categoryId()).isEqualTo(5L);
            assertThat(video.tagIds()).hasSizeBetween(2, 4);
            assertThat(video.publishedAt()).isNotNull();
            assertThat(video.potentialScore()).isBetween(0.0D, 1.0D);
        });
        assertThat(videos.stream().filter(video -> video.authorId().equals(101L)).mapToDouble(SeedVideoProfile::potentialScore).average().orElseThrow())
                .isGreaterThan(videos.stream().filter(video -> video.authorId().equals(102L)).mapToDouble(SeedVideoProfile::potentialScore).average().orElseThrow());
        assertThat(videos.stream().filter(video -> video.authorId().equals(102L)).mapToDouble(SeedVideoProfile::potentialScore).average().orElseThrow())
                .isGreaterThan(videos.stream().filter(video -> video.authorId().equals(103L)).mapToDouble(SeedVideoProfile::potentialScore).average().orElseThrow());

        verify(fixture.featureService, times(12)).syncVideoTagFeatures(any(Long.class), anyList(), eq("seed"), eq("v1"), any());
        verify(fixture.videoTagMapper, times(videos.stream().mapToInt(video -> video.tagIds().size()).sum())).insert(any(VideoTag.class));

        ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
        verify(fixture.videoMapper, times(12)).insert(videoCaptor.capture());
        assertThat(videoCaptor.getAllValues()).allSatisfy(video -> {
            assertThat(video.getTitle()).isNotBlank();
            assertThat(video.getDescription()).contains("科技");
            assertThat(video.getCoverUrl()).contains("seed://cover/");
            assertThat(video.getPreviewUrl()).contains("seed://preview/");
            assertThat(video.getVideoUrl()).contains("seed://video/");
            assertThat(video.getDurationSeconds()).isGreaterThan(0);
            assertThat(video.getCreateTime()).isNotNull();
        });
    }

    @Test
    void shouldKeepRecommendationOrderingAcrossAuthorTiersWhileAllowingTailSelection() {
        TestFixture fixture = new TestFixture();

        fixture.generator.generate(
                fixture.profile,
                fixture.snapshot,
                fixture.populationWithTiers(
                        SeedAuthorProfile.AuthorTier.HEAD,
                        SeedAuthorProfile.AuthorTier.MID,
                        SeedAuthorProfile.AuthorTier.TAIL
                ),
                SequenceRandom.withRecommendationPattern()
        );

        ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
        verify(fixture.videoMapper, times(12)).insert(videoCaptor.capture());
        Map<Long, Long> insertedRecommendedCounts = videoCaptor.getAllValues().stream()
                .collect(Collectors.groupingBy(
                        Video::getAuthorId,
                        Collectors.filtering(video -> Boolean.TRUE.equals(video.getIsRecommended()), Collectors.counting())
                ));

        assertThat(insertedRecommendedCounts.getOrDefault(101L, 0L)).isGreaterThan(insertedRecommendedCounts.getOrDefault(102L, 0L));
        assertThat(insertedRecommendedCounts.getOrDefault(102L, 0L)).isGreaterThan(insertedRecommendedCounts.getOrDefault(103L, 0L));
        assertThat(insertedRecommendedCounts.getOrDefault(103L, 0L)).isPositive();
    }

    @Test
    void shouldAssignUniformConfidenceToSelectedTags() {
        TestFixture fixture = new TestFixture();

        fixture.generator.generate(
                fixture.profile,
                fixture.snapshot,
                fixture.populationWithTiers(SeedAuthorProfile.AuthorTier.HEAD),
                new Random(7L)
        );

        ArgumentCaptor<Map<Long, Double>> confidenceCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.featureService, times(12)).syncVideoTagFeatures(any(Long.class), anyList(), eq("seed"), eq("v1"), confidenceCaptor.capture());
        assertThat(confidenceCaptor.getAllValues()).allSatisfy(confidenceByTagId -> {
            assertThat(confidenceByTagId).isNotEmpty();
            assertThat(confidenceByTagId.values()).allSatisfy(confidence -> assertThat(confidence).isEqualTo(0.9D));
        });
    }

    @Test
    void shouldRejectClusterWithoutValidSeedInputs() {
        TestFixture fixture = new TestFixture();

        assertThatThrownBy(() -> fixture.generator.generate(
                fixture.profile,
                fixture.snapshotWithCluster(new InterestCluster(
                        "technology",
                        "科技",
                        List.of(),
                        List.of(21L),
                        List.of("实战"),
                        List.of("项目")
                )),
                fixture.populationWithTiers(SeedAuthorProfile.AuthorTier.HEAD),
                new Random(1L)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("categoryIds");

        assertThatThrownBy(() -> fixture.generator.generate(
                fixture.profile,
                fixture.snapshotWithCluster(new InterestCluster(
                        "technology",
                        "科技",
                        List.of(5L),
                        List.of(),
                        List.of("实战"),
                        List.of("项目")
                )),
                fixture.populationWithTiers(SeedAuthorProfile.AuthorTier.HEAD),
                new Random(1L)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tagIds");

        assertThatThrownBy(() -> fixture.generator.generate(
                fixture.profile,
                fixture.snapshotWithCluster(new InterestCluster(
                        "technology",
                        "科技",
                        List.of(5L),
                        List.of(21L),
                        List.of("  "),
                        List.of("项目")
                )),
                fixture.populationWithTiers(SeedAuthorProfile.AuthorTier.HEAD),
                new Random(1L)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("titlePrefixes");

        assertThatThrownBy(() -> fixture.generator.generate(
                fixture.profile,
                fixture.snapshotWithCluster(new InterestCluster(
                        "technology",
                        "科技",
                        List.of(5L),
                        List.of(21L),
                        List.of("实战"),
                        List.of(" ")
                )),
                fixture.populationWithTiers(SeedAuthorProfile.AuthorTier.HEAD),
                new Random(1L)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("titleKeywords");
    }

    private static final class TestFixture {
        private final VideoMapper videoMapper = mock(VideoMapper.class);
        private final VideoTagMapper videoTagMapper = mock(VideoTagMapper.class);
        private final RecommendationFeatureService featureService = mock(RecommendationFeatureService.class);
        private final SeedVideoGenerator generator = new SeedVideoGenerator(videoMapper, videoTagMapper, featureService);
        private final SeedProfile profile = new SeedProfile(3, 4, 12, 100, 20, 10, 5,
                0.10D, 0.30D, 0.45D, 0.15D,
                0.70D, 0.20D, 0.10D, 30);
        private final InterestCluster technology = new InterestCluster(
                "technology", "科技", List.of(5L), List.of(21L, 22L, 23L, 24L), List.of("实战", "拆解"), List.of("项目", "教程")
        );
        private final SeedDomainSnapshot snapshot = new SeedDomainSnapshot(
                List.of(technology),
                Map.of("technology", technology),
                List.of(5L),
                List.of(21L, 22L, 23L, 24L)
        );

        private TestFixture() {
            AtomicLong ids = new AtomicLong(10);
            doAnswer(invocation -> {
                Video video = invocation.getArgument(0);
                video.setId(ids.incrementAndGet());
                return 1;
            }).when(videoMapper).insert(any(Video.class));
        }

        private SeedPopulation populationWithTiers(SeedAuthorProfile.AuthorTier... tiers) {
            List<SeedAuthorProfile> authors = new ArrayList<>();
            for (int i = 0; i < tiers.length; i++) {
                authors.add(SeedAuthorProfile.of(101L + i, "up_tech_" + (i + 1), tiers[i], "technology"));
            }
            return new SeedPopulation(authors, List.of(), List.of());
        }

        private SeedDomainSnapshot snapshotWithCluster(InterestCluster cluster) {
            return new SeedDomainSnapshot(
                    List.of(cluster),
                    Map.of(cluster.key(), cluster),
                    cluster.categoryIds(),
                    cluster.tagIds()
            );
        }
    }

    private static final class SequenceRandom extends Random {
        private final Deque<Double> doubles;
        private int nextIntCounter;

        private SequenceRandom(List<Double> doubles) {
            this.doubles = new ArrayDeque<>(doubles);
        }

        private static SequenceRandom withRecommendationPattern() {
            return new SequenceRandom(List.of(
                    0.10D, 0.30D,
                    0.10D, 0.60D,
                    0.10D, 0.70D,
                    0.10D, 0.50D,
                    0.10D, 0.50D,
                    0.10D, 0.10D,
                    0.10D, 0.45D,
                    0.10D, 0.10D,
                    0.10D, 0.20D,
                    0.10D, 0.20D,
                    0.10D, 0.20D,
                    0.10D, 0.10D
            ));
        }

        @Override
        public double nextDouble() {
            return doubles.isEmpty() ? 0.0D : doubles.removeFirst();
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            int value = nextIntCounter % bound;
            nextIntCounter++;
            return value;
        }
    }
}