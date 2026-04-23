package com.bilibili.video.seed;

import com.bilibili.video.entity.Favorite;
import com.bilibili.video.entity.Follow;
import com.bilibili.video.entity.VideoLike;
import com.bilibili.video.entity.WatchHistory;

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