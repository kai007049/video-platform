package com.kai.videoplatform.seed;

import com.kai.videoplatform.entity.User;
import com.kai.videoplatform.mapper.UserMapper;
import com.kai.videoplatform.utils.MyBCr;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeedUserGeneratorTest {

    @Test
    void shouldConservePersonaTotalsWithLargestRemainderAllocation() {
        SeedProfile profile = new SeedProfile(2, 10, 40, 100, 20, 10, 5,
                0.26D, 0.26D, 0.26D, 0.22D,
                0.70D, 0.20D, 0.10D, 30);

        List<SeedUserPersona> personas = SeedUserGenerator.planUserPersonas(profile);

        assertThat(personas).hasSize(10);
        assertThat(personas).containsExactly(
                SeedUserPersona.HEAVY,
                SeedUserPersona.HEAVY,
                SeedUserPersona.HEAVY,
                SeedUserPersona.MEDIUM,
                SeedUserPersona.MEDIUM,
                SeedUserPersona.MEDIUM,
                SeedUserPersona.LIGHT,
                SeedUserPersona.LIGHT,
                SeedUserPersona.COLD,
                SeedUserPersona.COLD
        );
    }

    @Test
    void shouldResolveAuthorTierBoundariesForSmallAuthorCounts() {
        assertThat(SeedUserGenerator.resolveAuthorTier(0, 1)).isEqualTo(SeedAuthorProfile.AuthorTier.HEAD);
        assertThat(SeedUserGenerator.resolveAuthorTier(0, 2)).isEqualTo(SeedAuthorProfile.AuthorTier.HEAD);
        assertThat(SeedUserGenerator.resolveAuthorTier(1, 2)).isEqualTo(SeedAuthorProfile.AuthorTier.TAIL);
        assertThat(SeedUserGenerator.resolveAuthorTier(0, 3)).isEqualTo(SeedAuthorProfile.AuthorTier.HEAD);
        assertThat(SeedUserGenerator.resolveAuthorTier(1, 3)).isEqualTo(SeedAuthorProfile.AuthorTier.MID);
        assertThat(SeedUserGenerator.resolveAuthorTier(2, 3)).isEqualTo(SeedAuthorProfile.AuthorTier.TAIL);
    }

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
                Map.of(
                        "gaming", new InterestCluster("gaming", "游戏", List.of(1L), List.of(11L), List.of("上分"), List.of("攻略")),
                        "technology", new InterestCluster("technology", "科技", List.of(2L), List.of(12L), List.of("实战"), List.of("项目"))
                ),
                List.of(1L, 2L),
                List.of(11L, 12L)
        );

        SeedPopulation population = generator.generate(profile, snapshot, new Random(42L));

        assertThat(population.authors()).hasSize(4);
        assertThat(population.users()).hasSize(12);
        assertThat(population.rawUsers()).hasSize(16);
        assertThat(population.users()).extracting(SeedUserProfile::persona)
                .containsExactlyInAnyOrder(
                        SeedUserPersona.HEAVY,
                        SeedUserPersona.HEAVY,
                        SeedUserPersona.HEAVY,
                        SeedUserPersona.MEDIUM,
                        SeedUserPersona.MEDIUM,
                        SeedUserPersona.MEDIUM,
                        SeedUserPersona.LIGHT,
                        SeedUserPersona.LIGHT,
                        SeedUserPersona.LIGHT,
                        SeedUserPersona.COLD,
                        SeedUserPersona.COLD,
                        SeedUserPersona.COLD
                );
        assertThat(population.rawUsers()).allSatisfy(user -> {
            assertThat(MyBCr.matches("SeedPass123!", user.getPassword())).isTrue();
            assertThat(user.getPassword()).isNotEqualTo("SeedPass123!");
            assertThat(user.getIsAdmin()).isFalse();
        });
    }

    @Test
    void shouldAssignPrimaryAndSecondaryClustersAndAuthorTiers() {
        UserMapper userMapper = mock(UserMapper.class);
        AtomicLong ids = new AtomicLong(200);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(ids.incrementAndGet());
            return 1;
        }).when(userMapper).insert(any(User.class));

        SeedUserGenerator generator = new SeedUserGenerator(userMapper);
        SeedProfile profile = new SeedProfile(6, 8, 40, 100, 20, 10, 5,
                0.25D, 0.25D, 0.25D, 0.25D,
                0.70D, 0.20D, 0.10D, 30);
        SeedDomainSnapshot snapshot = new SeedDomainSnapshot(
                List.of(
                        new InterestCluster("gaming", "游戏", List.of(1L), List.of(11L), List.of("上分"), List.of("攻略")),
                        new InterestCluster("technology", "科技", List.of(2L), List.of(12L), List.of("实战"), List.of("项目")),
                        new InterestCluster("music", "音乐", List.of(3L), List.of(13L), List.of("翻唱"), List.of("现场"))
                ),
                Map.of(
                        "gaming", new InterestCluster("gaming", "游戏", List.of(1L), List.of(11L), List.of("上分"), List.of("攻略")),
                        "technology", new InterestCluster("technology", "科技", List.of(2L), List.of(12L), List.of("实战"), List.of("项目")),
                        "music", new InterestCluster("music", "音乐", List.of(3L), List.of(13L), List.of("翻唱"), List.of("现场"))
                ),
                List.of(1L, 2L, 3L),
                List.of(11L, 12L, 13L)
        );

        SeedPopulation population = generator.generate(profile, snapshot, new Random(7L));

        assertThat(population.authors()).extracting(SeedAuthorProfile::tier)
                .contains(
                        SeedAuthorProfile.AuthorTier.HEAD,
                        SeedAuthorProfile.AuthorTier.MID,
                        SeedAuthorProfile.AuthorTier.TAIL
                );
        assertThat(population.authors()).allSatisfy(author ->
                assertThat(author.clusterKey()).isIn("gaming", "technology", "music"));
        assertThat(population.users()).allSatisfy(user -> {
            assertThat(user.primaryClusterKey()).isIn("gaming", "technology", "music");
            assertThat(user.secondaryClusterKey()).isIn("gaming", "technology", "music");
            assertThat(user.primaryClusterKey()).isNotBlank();
            assertThat(user.secondaryClusterKey()).isNotBlank();
        });
    }

    @Test
    void shouldAppendWithoutReusingExistingUsernames() {
        UserMapper userMapper = mock(UserMapper.class);
        AtomicLong ids = new AtomicLong(300);
        AtomicInteger usernameChecks = new AtomicInteger();
        when(userMapper.selectCount(any())).thenAnswer(invocation ->
                usernameChecks.getAndIncrement() % 2 == 0 ? 1L : 0L);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(ids.incrementAndGet());
            return 1;
        }).when(userMapper).insert(any(User.class));

        SeedUserGenerator generator = new SeedUserGenerator(userMapper);
        SeedProfile profile = new SeedProfile(2, 2, 40, 100, 20, 10, 5,
                0.25D, 0.25D, 0.25D, 0.25D,
                0.70D, 0.20D, 0.10D, 30);
        SeedDomainSnapshot snapshot = new SeedDomainSnapshot(
                List.of(
                        new InterestCluster("gaming", "游戏", List.of(1L), List.of(11L), List.of("上分"), List.of("攻略")),
                        new InterestCluster("technology", "科技", List.of(2L), List.of(12L), List.of("实战"), List.of("项目"))
                ),
                Map.of(
                        "gaming", new InterestCluster("gaming", "游戏", List.of(1L), List.of(11L), List.of("上分"), List.of("攻略")),
                        "technology", new InterestCluster("technology", "科技", List.of(2L), List.of(12L), List.of("实战"), List.of("项目"))
                ),
                List.of(1L, 2L),
                List.of(11L, 12L)
        );

        SeedPopulation population = generator.generate(profile, snapshot, new Random(3L));

        assertThat(population.rawUsers())
                .extracting(User::getUsername)
                .doesNotContain("up_gaming_1000", "up_technology_1001", "technology_user_2000", "technology_user_2001")
                .allMatch(username -> username.endsWith("_2"))
                .doesNotHaveDuplicates();
    }
}