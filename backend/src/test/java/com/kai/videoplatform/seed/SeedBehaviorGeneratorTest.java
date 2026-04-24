package com.kai.videoplatform.seed;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedBehaviorGeneratorTest {

    @Test
    void shouldGenerateMoreWatchesForHeavyUsersThanColdUsers() {
        SeedBehaviorResult result = generate(profile(), snapshot(), standardVideos(), new Random(42L));

        long heavyWatchCount = result.watches().stream().filter(item -> item.getUserId().equals(201L)).count();
        long coldWatchCount = result.watches().stream().filter(item -> item.getUserId().equals(204L)).count();

        assertThat(heavyWatchCount).isGreaterThan(coldWatchCount);
        assertThat(result.playCountByVideo()).isNotEmpty();
        assertThat(result.likeCountByVideo()).isNotEmpty();
        assertThat(result.favoriteCountByVideo()).isNotEmpty();
        assertThat(result.userInterestWeights()).containsKey(201L);
    }

    @Test
    void shouldRejectEmptyVideos() {
        assertThatThrownBy(() -> generate(profile(), snapshot(), List.of(), new Random(42L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("videos")
                .hasMessageContaining("must not be empty");
    }

    @Test
    void shouldRejectEmptyClusters() {
        SeedDomainSnapshot snapshot = new SeedDomainSnapshot(List.of(), Map.of(), List.of(1L, 2L), List.of(11L, 12L));

        assertThatThrownBy(() -> generate(profile(), snapshot, standardVideos(), new Random(42L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clusters")
                .hasMessageContaining("must not be empty");
    }

    @Test
    void shouldNotGenerateFutureWatchedAt() {
        LocalDateTime now = LocalDateTime.now();
        List<SeedVideoProfile> videos = List.of(
                new SeedVideoProfile(401L, 101L, "gaming", 1L, List.of(11L, 12L), false, 1.0D, now.minusMinutes(10)),
                new SeedVideoProfile(402L, 102L, "technology", 2L, List.of(21L, 22L), false, 0.8D, now.minusMinutes(30))
        );

        SeedBehaviorResult result = generate(profile(), snapshot(), videos, new Random(7L));

        assertThat(result.watches())
                .isNotEmpty()
                .allSatisfy(watch -> assertThat(watch.getCreateTime()).isBeforeOrEqualTo(LocalDateTime.now()));
    }

    @Test
    void shouldAvoidAmplifyingDuplicateWatchSamplingForSingleUserVideoPair() {
        SeedPopulation singleUserPopulation = new SeedPopulation(
                List.of(SeedAuthorProfile.of(101L, "up_gaming", SeedAuthorProfile.AuthorTier.HEAD, "gaming")),
                List.of(new SeedUserProfile(201L, "heavy", SeedUserPersona.HEAVY, "gaming", "technology", 0.18D, 0.01D)),
                List.of()
        );
        List<SeedVideoProfile> videos = List.of(
                new SeedVideoProfile(501L, 101L, "gaming", 1L, List.of(11L), false, 1.0D, LocalDateTime.now().minusDays(2)),
                new SeedVideoProfile(502L, 102L, "technology", 2L, List.of(21L), false, 0.1D, LocalDateTime.now().minusDays(2))
        );

        SeedBehaviorResult result = new SeedBehaviorGenerator().generate(profile(), snapshot(), singleUserPopulation, videos, new Random(1L));

        Map<String, Long> watchCountsByUserVideo = result.watches().stream()
                .collect(Collectors.groupingBy(item -> item.getUserId() + ":" + item.getVideoId(), Collectors.counting()));

        assertThat(watchCountsByUserVideo.values()).allMatch(count -> count <= 3L);
    }

    @Test
    void shouldCapWatchesWhenAllCandidateVideosReachPerUserLimit() {
        SeedPopulation singleHeavyUserPopulation = new SeedPopulation(
                List.of(SeedAuthorProfile.of(101L, "up_gaming", SeedAuthorProfile.AuthorTier.HEAD, "gaming")),
                List.of(new SeedUserProfile(201L, "heavy", SeedUserPersona.HEAVY, "gaming", "technology", 0.18D, 0.01D)),
                List.of()
        );
        SeedProfile highWatchTargetProfile = new SeedProfile(2, 4, 12, 500, 20, 10, 5,
                0.25D, 0.25D, 0.25D, 0.25D,
                0.70D, 0.20D, 0.10D, 30);
        List<SeedVideoProfile> tinyVideoPool = List.of(
                new SeedVideoProfile(601L, 101L, "gaming", 1L, List.of(11L), false, 1.0D, LocalDateTime.now().minusDays(2))
        );

        SeedBehaviorResult result = new SeedBehaviorGenerator().generate(
                highWatchTargetProfile,
                snapshot(),
                singleHeavyUserPopulation,
                tinyVideoPool,
                new Random(1L)
        );

        assertThat(result.watches()).hasSize(3);
        assertThat(result.watches()).hasSizeLessThanOrEqualTo(tinyVideoPool.size() * 3);
        assertThat(result.watches()).allSatisfy(watch -> {
            assertThat(watch.getUserId()).isEqualTo(201L);
            assertThat(watch.getVideoId()).isEqualTo(601L);
        });
    }

    @Test
    void shouldNotGenerateDuplicateRelationshipBehaviors() {
        SeedBehaviorResult result = generate(profile(), snapshot(), standardVideos(), new Random(42L));

        assertThat(result.follows().stream().map(item -> item.getFollowerId() + ":" + item.getFollowingId()).distinct().count())
                .isEqualTo(result.follows().size());
        assertThat(result.likes().stream().map(item -> item.getUserId() + ":" + item.getVideoId()).distinct().count())
                .isEqualTo(result.likes().size());
        assertThat(result.favorites().stream().map(item -> item.getUserId() + ":" + item.getVideoId()).distinct().count())
                .isEqualTo(result.favorites().size());
    }

    private SeedBehaviorResult generate(SeedProfile profile, SeedDomainSnapshot snapshot, List<SeedVideoProfile> videos, Random random) {
        return new SeedBehaviorGenerator().generate(profile, snapshot, population(), videos, random);
    }

    private SeedProfile profile() {
        return new SeedProfile(2, 4, 12, 120, 20, 10, 5,
                0.25D, 0.25D, 0.25D, 0.25D,
                0.70D, 0.20D, 0.10D, 30);
    }

    private SeedPopulation population() {
        return new SeedPopulation(
                List.of(
                        SeedAuthorProfile.of(101L, "up_gaming", SeedAuthorProfile.AuthorTier.HEAD, "gaming"),
                        SeedAuthorProfile.of(102L, "up_tech", SeedAuthorProfile.AuthorTier.TAIL, "technology")
                ),
                List.of(
                        new SeedUserProfile(201L, "heavy", SeedUserPersona.HEAVY, "gaming", "technology", 0.18D, 0.08D),
                        new SeedUserProfile(202L, "medium", SeedUserPersona.MEDIUM, "gaming", "technology", 0.10D, 0.08D),
                        new SeedUserProfile(203L, "light", SeedUserPersona.LIGHT, "technology", "gaming", 0.04D, 0.08D),
                        new SeedUserProfile(204L, "cold", SeedUserPersona.COLD, "technology", "gaming", 0.02D, 0.02D)
                ),
                List.of()
        );
    }

    private SeedDomainSnapshot snapshot() {
        InterestCluster gaming = new InterestCluster("gaming", "游戏", List.of(1L), List.of(11L, 12L), List.of("上分"), List.of("攻略"));
        InterestCluster technology = new InterestCluster("technology", "科技", List.of(2L), List.of(21L, 22L), List.of("实战"), List.of("项目"));
        return new SeedDomainSnapshot(
                List.of(gaming, technology),
                Map.of("gaming", gaming, "technology", technology),
                List.of(1L, 2L),
                List.of(11L, 12L, 21L, 22L)
        );
    }

    private List<SeedVideoProfile> standardVideos() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                new SeedVideoProfile(301L, 101L, "gaming", 1L, List.of(11L, 12L), false, 1.0D, now.minusDays(1)),
                new SeedVideoProfile(302L, 101L, "gaming", 1L, List.of(11L), false, 0.8D, now.minusDays(2)),
                new SeedVideoProfile(303L, 102L, "technology", 2L, List.of(21L, 22L), false, 0.7D, now.minusDays(1)),
                new SeedVideoProfile(304L, 102L, "technology", 2L, List.of(21L), true, 0.6D, now.minusDays(3))
        );
    }
}