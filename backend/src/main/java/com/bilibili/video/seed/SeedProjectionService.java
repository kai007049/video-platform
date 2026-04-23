package com.bilibili.video.seed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.common.Constants;
import com.bilibili.video.entity.Favorite;
import com.bilibili.video.entity.Follow;
import com.bilibili.video.entity.UserInterestTag;
import com.bilibili.video.entity.Video;
import com.bilibili.video.entity.VideoLike;
import com.bilibili.video.entity.WatchHistory;
import com.bilibili.video.mapper.FavoriteMapper;
import com.bilibili.video.mapper.FollowMapper;
import com.bilibili.video.mapper.UserInterestTagMapper;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.mapper.WatchHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SeedProjectionService {

    private final WatchHistoryMapper watchHistoryMapper;
    private final VideoLikeMapper videoLikeMapper;
    private final FavoriteMapper favoriteMapper;
    private final FollowMapper followMapper;
    private final UserInterestTagMapper userInterestTagMapper;
    private final VideoMapper videoMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public void persist(SeedBehaviorResult result) {
        for (WatchHistory watch : result.watches()) {
            upsertWatch(watch);
        }
        for (VideoLike like : result.likes()) {
            videoLikeMapper.insert(like);
        }
        for (Favorite favorite : result.favorites()) {
            favoriteMapper.insert(favorite);
        }
        for (Follow follow : result.follows()) {
            followMapper.insert(follow);
        }
        persistUserInterestWeights(result.userInterestWeights());
        persistVideoStatsAndHotRank(result);
    }

    private void upsertWatch(WatchHistory watch) {
        WatchHistory existing = watchHistoryMapper.selectOne(new LambdaQueryWrapper<WatchHistory>()
                .eq(WatchHistory::getUserId, watch.getUserId())
                .eq(WatchHistory::getVideoId, watch.getVideoId())
                .last("limit 1"));
        if (existing == null) {
            watchHistoryMapper.insert(watch);
            return;
        }

        existing.setWatchSeconds(mergeWatchSeconds(existing.getWatchSeconds(), watch.getWatchSeconds()));
        existing.setUpdateTime(resolveLatestTime(existing.getUpdateTime(), watch.getUpdateTime(), watch.getCreateTime()));
        watchHistoryMapper.updateById(existing);
    }

    private Integer mergeWatchSeconds(Integer existingSeconds, Integer incomingSeconds) {
        int existingValue = existingSeconds == null ? 0 : existingSeconds;
        int incomingValue = incomingSeconds == null ? 0 : incomingSeconds;
        return Math.max(existingValue, incomingValue);
    }

    private void persistUserInterestWeights(Map<Long, Map<Long, Double>> weightsByUser) {
        for (Map.Entry<Long, Map<Long, Double>> userEntry : weightsByUser.entrySet()) {
            for (Map.Entry<Long, Double> tagEntry : userEntry.getValue().entrySet()) {
                UserInterestTag existing = userInterestTagMapper.selectOne(new LambdaQueryWrapper<UserInterestTag>()
                        .eq(UserInterestTag::getUserId, userEntry.getKey())
                        .eq(UserInterestTag::getTagId, tagEntry.getKey())
                        .last("limit 1"));
                if (existing != null) {
                    double existingWeight = existing.getWeight() == null ? 0D : existing.getWeight();
                    existing.setWeight(existingWeight + tagEntry.getValue());
                    existing.setUpdatedAt(LocalDateTime.now());
                    userInterestTagMapper.updateById(existing);
                    continue;
                }

                UserInterestTag row = new UserInterestTag();
                row.setUserId(userEntry.getKey());
                row.setTagId(tagEntry.getKey());
                row.setWeight(tagEntry.getValue());
                row.setUpdatedAt(LocalDateTime.now());
                userInterestTagMapper.insert(row);
            }
        }
    }

    private void persistVideoStatsAndHotRank(SeedBehaviorResult result) {
        String key = Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h";
        for (Map.Entry<Long, Long> entry : result.playCountByVideo().entrySet()) {
            Long videoId = entry.getKey();
            long playCount = entry.getValue();
            long likeCount = result.likeCountByVideo().getOrDefault(videoId, 0L);
            long favoriteCount = result.favoriteCountByVideo().getOrDefault(videoId, 0L);

            Video existing = videoMapper.selectById(videoId);
            Video video = new Video();
            video.setId(videoId);
            video.setPlayCount((existing == null || existing.getPlayCount() == null ? 0L : existing.getPlayCount()) + playCount);
            video.setLikeCount((existing == null || existing.getLikeCount() == null ? 0L : existing.getLikeCount()) + likeCount);
            video.setSaveCount((existing == null || existing.getSaveCount() == null ? 0L : existing.getSaveCount()) + favoriteCount);
            videoMapper.updateById(video);

            double hotScore = playCount * Constants.HOT_WEIGHT_PLAY
                    + likeCount * Constants.HOT_WEIGHT_LIKE
                    + favoriteCount * Constants.HOT_WEIGHT_FAVORITE;
            redisTemplate.opsForZSet().incrementScore(key, String.valueOf(videoId), hotScore);
        }
        redisTemplate.expire(key, Constants.HOT_WINDOW_HOURS, TimeUnit.HOURS);
    }

    private LocalDateTime resolveLatestTime(LocalDateTime existingTime, LocalDateTime incomingUpdateTime, LocalDateTime incomingCreateTime) {
        LocalDateTime incomingTime = incomingUpdateTime != null ? incomingUpdateTime : incomingCreateTime;
        if (existingTime == null) {
            return incomingTime;
        }
        if (incomingTime == null || existingTime.isAfter(incomingTime)) {
            return existingTime;
        }
        return incomingTime;
    }
}