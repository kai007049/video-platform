package com.kai.videoplatform.seed;

import com.kai.videoplatform.entity.Favorite;
import com.kai.videoplatform.entity.Follow;
import com.kai.videoplatform.entity.VideoLike;
import com.kai.videoplatform.entity.WatchHistory;

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