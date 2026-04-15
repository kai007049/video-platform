package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.entity.VideoLike;
import com.bilibili.video.common.Constants;
import com.bilibili.video.common.RedisConstants;
import com.bilibili.video.exception.BizException;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.bilibili.video.service.LikeService;
import com.bilibili.video.service.MQService;
import com.bilibili.video.service.RecommendationFeatureService;
import com.bilibili.video.service.VideoCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 点赞服务实现 - Redis 缓存点赞状态
 */
@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

    private static final String LIKE_KEY = RedisConstants.VIDEO_LIKE_KEY_PREFIX;
    private static final long LIKE_EXPIRE_DAYS = RedisConstants.VIDEO_LIKE_EXPIRE_DAYS;

    private final VideoLikeMapper videoLikeMapper;
    private final VideoMapper videoMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MQService mqService;
    private final RecommendationFeatureService recommendationFeatureService;
    private final VideoCacheService videoCacheService;

    @Override
    public void like(Long videoId, Long userId) {
        var video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new BizException(404, "视频不存在");
        }

        long count = videoLikeMapper.selectCount(new LambdaQueryWrapper<VideoLike>()
                .eq(VideoLike::getVideoId, videoId)
                .eq(VideoLike::getUserId, userId));
        if (count > 0) {
            return;
        }

        VideoLike like = new VideoLike();
        like.setVideoId(videoId);
        like.setUserId(userId);
        videoLikeMapper.insert(like);


        String statsKey = RedisConstants.VIDEO_STATS_KEY_PREFIX + videoId;
        redisTemplate.opsForHash().increment(statsKey, RedisConstants.VIDEO_STAT_LIKE, 1);
        redisTemplate.expire(statsKey, RedisConstants.VIDEO_STATS_EXPIRE_DAYS, RedisConstants.DEFAULT_TIME_UNIT_DAYS);

        if (video.getAuthorId() != null && !video.getAuthorId().equals(userId)) {
            mqService.sendNotify(new NotifyMessage("like", video.getAuthorId(), videoId, null));
        }
        mqService.sendSearchSync(new SearchSyncMessage("video", videoId, "update"));
        recommendationFeatureService.increaseUserInterestByVideo(userId, videoId, 3.0D);

        redisTemplate.opsForZSet().incrementScore(
                Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h",
                String.valueOf(videoId),
                Constants.HOT_WEIGHT_LIKE
        );
        redisTemplate.expire(Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h", Constants.HOT_WINDOW_HOURS, TimeUnit.HOURS);

        String key = LIKE_KEY + videoId + ":" + userId;
        redisTemplate.opsForValue().set(key, "1", LIKE_EXPIRE_DAYS, TimeUnit.DAYS);
        // 点赞状态会影响详情页展示的点赞数与已点赞状态，因此这里主动失效视频详情缓存。
        videoCacheService.invalidateVideo(videoId);
    }

    @Override
    public void unlike(Long videoId, Long userId) {
        int deleted = videoLikeMapper.delete(new LambdaQueryWrapper<VideoLike>()
                .eq(VideoLike::getVideoId, videoId)
                .eq(VideoLike::getUserId, userId));
        if (deleted > 0) {
            String statsKey = RedisConstants.VIDEO_STATS_KEY_PREFIX + videoId;
            redisTemplate.opsForHash().increment(statsKey, RedisConstants.VIDEO_STAT_LIKE, -1);
            redisTemplate.expire(statsKey, RedisConstants.VIDEO_STATS_EXPIRE_DAYS, RedisConstants.DEFAULT_TIME_UNIT_DAYS);
        }

        String key = LIKE_KEY + videoId + ":" + userId;
        redisTemplate.delete(key);
        if (deleted > 0) {
            videoCacheService.invalidateVideo(videoId);
        }
    }

    @Override
    public boolean isLiked(Long videoId, Long userId) {
        if (userId == null) {
            return false;
        }
        String key = LIKE_KEY + videoId + ":" + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return true;
        }
        long count = videoLikeMapper.selectCount(new LambdaQueryWrapper<VideoLike>()
                .eq(VideoLike::getVideoId, videoId)
                .eq(VideoLike::getUserId, userId));
        if (count > 0) {
            redisTemplate.opsForValue().set(key, "1", LIKE_EXPIRE_DAYS, TimeUnit.DAYS);
            return true;
        }
        return false;
    }
}
