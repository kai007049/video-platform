package com.kai.videoplatform.seed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kai.videoplatform.entity.User;
import com.kai.videoplatform.mapper.UserMapper;
import com.kai.videoplatform.utils.MyBCr;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class SeedUserGenerator {

    private static final String SEED_PASSWORD = "SeedPass123!";
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
        List<InterestCluster> clusters = requireClusters(snapshot);
        GenerationPlan plan = plan(profile, clusters, random);

        List<SeedPersistedAuthor> persistedAuthors = persistAuthors(plan.authorPlans(), random);
        List<SeedPersistedUser> persistedUsers = persistUsers(plan.userPlans(), random);
        List<User> rawUsers = new ArrayList<>(persistedAuthors.size() + persistedUsers.size());
        rawUsers.addAll(persistedAuthors.stream().map(SeedPersistedAuthor::rawUser).toList());
        rawUsers.addAll(persistedUsers.stream().map(SeedPersistedUser::rawUser).toList());

        return new SeedPopulation(
                persistedAuthors.stream().map(SeedPersistedAuthor::profile).toList(),
                persistedUsers.stream().map(SeedPersistedUser::profile).toList(),
                List.copyOf(rawUsers)
        );
    }

    static List<SeedUserPersona> planUserPersonas(SeedProfile profile) {
        return allocatePersonas(profile.userCount(), List.of(
                new PersonaRatio(SeedUserPersona.HEAVY, profile.heavyUserRatio()),
                new PersonaRatio(SeedUserPersona.MEDIUM, profile.mediumUserRatio()),
                new PersonaRatio(SeedUserPersona.LIGHT, profile.lightUserRatio()),
                new PersonaRatio(SeedUserPersona.COLD, profile.coldUserRatio())
        ));
    }

    static SeedAuthorProfile.AuthorTier resolveAuthorTier(int index, int authorCount) {
        if (index < 0 || index >= authorCount) {
            throw new IllegalArgumentException("author index out of range");
        }
        int headCount = Math.max(1, authorCount / 10);
        int remainingAfterHead = Math.max(0, authorCount - headCount);
        int midCount = authorCount >= 3
                ? Math.max(1, Math.min(remainingAfterHead - 1, authorCount / 3 - headCount))
                : 0;
        if (index < headCount) {
            return SeedAuthorProfile.AuthorTier.HEAD;
        }
        if (index < headCount + midCount) {
            return SeedAuthorProfile.AuthorTier.MID;
        }
        return SeedAuthorProfile.AuthorTier.TAIL;
    }

    static GenerationPlan plan(SeedProfile profile, List<InterestCluster> clusters, Random random) {
        List<SeedAuthorPlan> authorPlans = planAuthors(profile.authorCount(), clusters);
        List<SeedUserPlan> userPlans = planUsers(profile, clusters, random);
        return new GenerationPlan(List.copyOf(authorPlans), List.copyOf(userPlans));
    }

    private static List<InterestCluster> requireClusters(SeedDomainSnapshot snapshot) {
        List<InterestCluster> clusters = snapshot.clusters();
        if (clusters.isEmpty()) {
            throw new IllegalArgumentException("SeedDomainSnapshot must contain at least one cluster");
        }
        return clusters;
    }

    private static List<SeedAuthorPlan> planAuthors(int authorCount, List<InterestCluster> clusters) {
        List<SeedAuthorPlan> plans = new ArrayList<>(authorCount);
        for (int i = 0; i < authorCount; i++) {
            InterestCluster cluster = clusters.get(i % clusters.size());
            plans.add(new SeedAuthorPlan(
                    "up_" + cluster.key() + "_" + (1000 + i),
                    cluster.key(),
                    resolveAuthorTier(i, authorCount)
            ));
        }
        return plans;
    }

    private static List<SeedUserPlan> planUsers(SeedProfile profile, List<InterestCluster> clusters, Random random) {
        List<SeedUserPersona> personas = planUserPersonas(profile);
        List<SeedUserPlan> plans = new ArrayList<>(profile.userCount());
        for (int i = 0; i < profile.userCount(); i++) {
            InterestCluster primary = clusters.get(random.nextInt(clusters.size()));
            InterestCluster secondary = pickSecondaryCluster(clusters, primary, random);
            SeedUserPersona persona = personas.get(i);
            plans.add(new SeedUserPlan(
                    primary.key() + "_user_" + (2000 + i),
                    persona,
                    primary.key(),
                    secondary.key(),
                    followBias(persona),
                    explorationBias(persona)
            ));
        }
        return plans;
    }

    private List<SeedPersistedAuthor> persistAuthors(List<SeedAuthorPlan> authorPlans, Random random) {
        List<SeedPersistedAuthor> authors = new ArrayList<>(authorPlans.size());
        for (SeedAuthorPlan plan : authorPlans) {
            User author = buildUser(nextAvailableUsername(plan.username()), random);
            userMapper.insert(author);
            authors.add(new SeedPersistedAuthor(
                    SeedAuthorProfile.of(author.getId(), author.getUsername(), plan.tier(), plan.clusterKey()),
                    author
            ));
        }
        return authors;
    }

    private List<SeedPersistedUser> persistUsers(List<SeedUserPlan> userPlans, Random random) {
        List<SeedPersistedUser> users = new ArrayList<>(userPlans.size());
        for (SeedUserPlan plan : userPlans) {
            User user = buildUser(nextAvailableUsername(plan.username()), random);
            userMapper.insert(user);
            users.add(new SeedPersistedUser(
                    new SeedUserProfile(
                            user.getId(),
                            user.getUsername(),
                            plan.persona(),
                            plan.primaryClusterKey(),
                            plan.secondaryClusterKey(),
                            plan.followBias(),
                            plan.explorationBias()
                    ),
                    user
            ));
        }
        return users;
    }

    private static InterestCluster pickSecondaryCluster(List<InterestCluster> clusters, InterestCluster primary, Random random) {
        if (clusters.size() == 1) {
            return primary;
        }
        InterestCluster secondary = clusters.get(random.nextInt(clusters.size()));
        while (secondary.key().equals(primary.key())) {
            secondary = clusters.get(random.nextInt(clusters.size()));
        }
        return secondary;
    }

    private static List<SeedUserPersona> allocatePersonas(int userCount, List<PersonaRatio> ratios) {
        List<PersonaAllocation> allocations = new ArrayList<>(ratios.size());
        int assigned = 0;
        for (int i = 0; i < ratios.size(); i++) {
            PersonaRatio ratio = ratios.get(i);
            double exact = userCount * ratio.ratio();
            int baseCount = (int) Math.floor(exact);
            assigned += baseCount;
            allocations.add(new PersonaAllocation(ratio.persona(), baseCount, exact - baseCount, i));
        }

        int remaining = userCount - assigned;
        allocations.sort(Comparator
                .comparingDouble(PersonaAllocation::remainder).reversed()
                .thenComparingInt(PersonaAllocation::originalIndex));
        for (int i = 0; i < remaining; i++) {
            PersonaAllocation allocation = allocations.get(i);
            allocations.set(i, allocation.withCount(allocation.count() + 1));
        }
        allocations.sort(Comparator.comparingInt(PersonaAllocation::originalIndex));

        List<SeedUserPersona> personas = new ArrayList<>(userCount);
        for (PersonaAllocation allocation : allocations) {
            for (int i = 0; i < allocation.count(); i++) {
                personas.add(allocation.persona());
            }
        }
        return List.copyOf(personas);
    }

    private static double followBias(SeedUserPersona persona) {
        return switch (persona) {
            case HEAVY -> 0.18D;
            case MEDIUM -> 0.10D;
            case LIGHT -> 0.04D;
            case COLD -> 0.02D;
        };
    }

    private static double explorationBias(SeedUserPersona persona) {
        return switch (persona) {
            case HEAVY, MEDIUM, LIGHT -> 0.08D;
            case COLD -> 0.02D;
        };
    }

    private String nextAvailableUsername(String baseUsername) {
        if (!usernameExists(baseUsername)) {
            return baseUsername;
        }
        int suffix = 2;
        while (usernameExists(baseUsername + "_" + suffix)) {
            suffix++;
        }
        return baseUsername + "_" + suffix;
    }

    private boolean usernameExists(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)) > 0;
    }

    private User buildUser(String username, Random random) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(MyBCr.encode(SEED_PASSWORD));
        user.setAvatar(DEFAULT_AVATARS[random.nextInt(DEFAULT_AVATARS.length)]);
        user.setIsAdmin(false);
        user.setCreateTime(LocalDateTime.now().minusDays(random.nextInt(120)));
        return user;
    }

    record GenerationPlan(List<SeedAuthorPlan> authorPlans, List<SeedUserPlan> userPlans) {
    }

    private record PersonaRatio(SeedUserPersona persona, double ratio) {
    }

    private record PersonaAllocation(SeedUserPersona persona, int count, double remainder, int originalIndex) {
        private PersonaAllocation withCount(int updatedCount) {
            return new PersonaAllocation(persona, updatedCount, remainder, originalIndex);
        }
    }

    record SeedAuthorPlan(String username, String clusterKey, SeedAuthorProfile.AuthorTier tier) {
    }

    record SeedUserPlan(
            String username,
            SeedUserPersona persona,
            String primaryClusterKey,
            String secondaryClusterKey,
            double followBias,
            double explorationBias
    ) {
    }

    private record SeedPersistedAuthor(SeedAuthorProfile profile, User rawUser) {
    }

    private record SeedPersistedUser(SeedUserProfile profile, User rawUser) {
    }
}