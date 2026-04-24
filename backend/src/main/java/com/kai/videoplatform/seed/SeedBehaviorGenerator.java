package com.kai.videoplatform.seed;

import com.kai.videoplatform.entity.Favorite;
import com.kai.videoplatform.entity.Follow;
import com.kai.videoplatform.entity.VideoLike;
import com.kai.videoplatform.entity.WatchHistory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SeedBehaviorGenerator {

    private static final int MAX_WATCHES_PER_USER_VIDEO = 3;

    public SeedBehaviorResult generate(
            SeedProfile profile,
            SeedDomainSnapshot snapshot,
            SeedPopulation population,
            List<SeedVideoProfile> videos,
            Random random
    ) {
        validateInputs(snapshot, videos, random);
        LocalDateTime now = LocalDateTime.now();
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
        Map<String, Integer> watchCountsByUserVideo = new LinkedHashMap<>();
        Set<String> likePairs = new LinkedHashSet<>();
        Set<String> favoritePairs = new LinkedHashSet<>();
        Set<String> followPairs = new LinkedHashSet<>();

        for (SeedUserProfile user : population.users()) {
            int targetViews = baseWatchCount(user.persona(), profile, population.users().size(), random);
            for (int i = 0; i < targetViews; i++) {
                SeedVideoProfile picked = pickVideo(user, snapshot, videosByCluster, videos, profile, watchCountsByUserVideo, random);
                if (picked == null) {
                    break;
                }
                String watchKey = user.userId() + ":" + picked.videoId();
                LocalDateTime watchedAt = normalizeWatchedAt(picked.publishedAt().plusHours(1L + random.nextInt(240)), now);

                WatchHistory watch = new WatchHistory();
                watch.setUserId(user.userId());
                watch.setVideoId(picked.videoId());
                watch.setWatchSeconds(buildWatchSeconds(user.persona(), random));
                watch.setCreateTime(watchedAt);
                watch.setUpdateTime(watchedAt);
                watches.add(watch);
                watchCountsByUserVideo.merge(watchKey, 1, Integer::sum);
                playCountByVideo.merge(picked.videoId(), 1L, Long::sum);

                Map<Long, Double> weights = userInterestWeights.computeIfAbsent(user.userId(), ignored -> new LinkedHashMap<>());
                double delta = interestDelta(user.persona());
                for (Long tagId : picked.tagIds()) {
                    weights.merge(tagId, delta, Double::sum);
                }

                if (random.nextDouble() < likeRate(user.persona())) {
                    String key = user.userId() + ":" + picked.videoId();
                    if (likePairs.add(key)) {
                        VideoLike like = new VideoLike();
                        like.setUserId(user.userId());
                        like.setVideoId(picked.videoId());
                        like.setCreateTime(normalizeWatchedAt(watchedAt.plusMinutes(1), now));
                        likes.add(like);
                        likeCountByVideo.merge(picked.videoId(), 1L, Long::sum);
                    }
                }

                if (random.nextDouble() < favoriteRate(user.persona())) {
                    String key = user.userId() + ":" + picked.videoId();
                    if (favoritePairs.add(key)) {
                        Favorite favorite = new Favorite();
                        favorite.setUserId(user.userId());
                        favorite.setVideoId(picked.videoId());
                        favorite.setCreateTime(normalizeWatchedAt(watchedAt.plusMinutes(2), now));
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
                        follow.setCreateTime(normalizeWatchedAt(watchedAt.plusMinutes(3), now));
                        follows.add(follow);
                    }
                }
            }
        }

        return new SeedBehaviorResult(
                watches,
                likes,
                favorites,
                follows,
                playCountByVideo,
                likeCountByVideo,
                favoriteCountByVideo,
                userInterestWeights
        );
    }

    private int baseWatchCount(SeedUserPersona persona, SeedProfile profile, int userCount, Random random) {
        int average = Math.max(1, profile.watchCount() / Math.max(1, userCount));
        int target = switch (persona) {
            case HEAVY -> Math.max(average + 8, Math.round(average * 1.8F));
            case MEDIUM -> Math.max(average, Math.round(average * 1.1F));
            case LIGHT -> Math.max(2, Math.round(average * 0.55F));
            case COLD -> Math.max(1, Math.round(average * 0.18F));
        };
        return target + random.nextInt(Math.max(2, average / 6 + 1));
    }

    private int buildWatchSeconds(SeedUserPersona persona, Random random) {
        int base = switch (persona) {
            case HEAVY -> 240;
            case MEDIUM -> 180;
            case LIGHT -> 120;
            case COLD -> 60;
        };
        return base + random.nextInt(240);
    }

    private double interestDelta(SeedUserPersona persona) {
        return switch (persona) {
            case HEAVY -> 1.8D;
            case MEDIUM -> 1.2D;
            case LIGHT -> 0.7D;
            case COLD -> 0.3D;
        };
    }

    private void validateInputs(SeedDomainSnapshot snapshot, List<SeedVideoProfile> videos, Random random) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(videos, "videos must not be null");
        Objects.requireNonNull(random, "random must not be null");
        if (videos.isEmpty()) {
            throw new IllegalArgumentException("videos must not be empty");
        }
        if (snapshot.clusters() == null || snapshot.clusters().isEmpty()) {
            throw new IllegalArgumentException("clusters must not be empty");
        }
    }

    private LocalDateTime normalizeWatchedAt(LocalDateTime timestamp, LocalDateTime now) {
        return timestamp.isAfter(now) ? now : timestamp;
    }

    private SeedVideoProfile pickVideo(
            SeedUserProfile user,
            SeedDomainSnapshot snapshot,
            Map<String, List<SeedVideoProfile>> videosByCluster,
            List<SeedVideoProfile> allVideos,
            SeedProfile profile,
            Map<String, Integer> watchCountsByUserVideo,
            Random random
    ) {
        List<SeedVideoProfile> preferredCandidates = eligibleCandidates(
                user,
                selectPreferredClusterKey(user, snapshot, profile, random),
                videosByCluster,
                allVideos,
                watchCountsByUserVideo
        );
        if (!preferredCandidates.isEmpty()) {
            return chooseCandidate(preferredCandidates, allVideos, user, watchCountsByUserVideo, random);
        }

        List<SeedVideoProfile> exploratoryCandidates = eligibleCandidates(
                user,
                selectExplorationClusterKey(snapshot, random),
                videosByCluster,
                allVideos,
                watchCountsByUserVideo
        );
        if (!exploratoryCandidates.isEmpty()) {
            return chooseCandidate(exploratoryCandidates, allVideos, user, watchCountsByUserVideo, random);
        }

        return chooseCandidate(
                eligibleCandidates(user, null, videosByCluster, allVideos, watchCountsByUserVideo),
                allVideos,
                user,
                watchCountsByUserVideo,
                random
        );
    }

    private String selectPreferredClusterKey(
            SeedUserProfile user,
            SeedDomainSnapshot snapshot,
            SeedProfile profile,
            Random random
    ) {
        double draw = random.nextDouble();
        if (draw < profile.primaryPreferenceWeight()) {
            return user.primaryClusterKey();
        }
        if (draw < profile.primaryPreferenceWeight() + profile.secondaryPreferenceWeight()) {
            return user.secondaryClusterKey();
        }
        if (random.nextDouble() < user.explorationBias()) {
            return selectExplorationClusterKey(snapshot, random);
        }
        return user.primaryClusterKey();
    }

    private String selectExplorationClusterKey(SeedDomainSnapshot snapshot, Random random) {
        return snapshot.clusters().get(random.nextInt(snapshot.clusters().size())).key();
    }

    private SeedVideoProfile chooseCandidate(
            List<SeedVideoProfile> candidates,
            List<SeedVideoProfile> allVideos,
            SeedUserProfile user,
            Map<String, Integer> watchCountsByUserVideo,
            Random random
    ) {
        if (candidates.isEmpty()) {
            return fallbackCandidate(allVideos, user, watchCountsByUserVideo, random);
        }
        SeedVideoProfile candidate = candidates.get(random.nextInt(candidates.size()));
        if (random.nextDouble() < 0.65D + candidate.potentialScore() * 0.2D) {
            return candidate;
        }
        return fallbackCandidate(allVideos, user, watchCountsByUserVideo, random);
    }

    private SeedVideoProfile fallbackCandidate(
            List<SeedVideoProfile> allVideos,
            SeedUserProfile user,
            Map<String, Integer> watchCountsByUserVideo,
            Random random
    ) {
        List<SeedVideoProfile> fallback = allVideos.stream()
                .filter(candidate -> watchCountsByUserVideo.getOrDefault(user.userId() + ":" + candidate.videoId(), 0) < MAX_WATCHES_PER_USER_VIDEO)
                .toList();
        if (fallback.isEmpty()) {
            return null;
        }
        return fallback.get(random.nextInt(fallback.size()));
    }

    private List<SeedVideoProfile> eligibleCandidates(
            SeedUserProfile user,
            String clusterKey,
            Map<String, List<SeedVideoProfile>> videosByCluster,
            List<SeedVideoProfile> allVideos,
            Map<String, Integer> watchCountsByUserVideo
    ) {
        List<SeedVideoProfile> source = clusterKey == null
                ? allVideos
                : videosByCluster.getOrDefault(clusterKey, allVideos);
        List<SeedVideoProfile> eligible = source.stream()
                .filter(candidate -> watchCountsByUserVideo.getOrDefault(user.userId() + ":" + candidate.videoId(), 0) < MAX_WATCHES_PER_USER_VIDEO)
                .toList();
        return eligible.isEmpty() ? allVideos.stream()
                .filter(candidate -> watchCountsByUserVideo.getOrDefault(user.userId() + ":" + candidate.videoId(), 0) < MAX_WATCHES_PER_USER_VIDEO)
                .toList() : eligible;
    }

    private double likeRate(SeedUserPersona persona) {
        return switch (persona) {
            case HEAVY -> 0.22D;
            case MEDIUM -> 0.14D;
            case LIGHT -> 0.08D;
            case COLD -> 0.03D;
        };
    }

    private double favoriteRate(SeedUserPersona persona) {
        return switch (persona) {
            case HEAVY -> 0.09D;
            case MEDIUM -> 0.05D;
            case LIGHT -> 0.02D;
            case COLD -> 0.005D;
        };
    }
}