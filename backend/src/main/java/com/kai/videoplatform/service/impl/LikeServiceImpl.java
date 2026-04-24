package com.kai.videoplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kai.videoplatform.entity.VideoLike;
import com.kai.videoplatform.common.Constants;
import com.kai.videoplatform.common.RedisConstants;
import com.kai.videoplatform.exception.BizException;
import com.kai.videoplatform.mapper.VideoLikeMapper;
import com.kai.videoplatform.mapper.VideoMapper;
import com.kai.videoplatform.model.mq.NotifyMessage;
import com.kai.videoplatform.model.mq.SearchSyncMessage;
import com.kai.videoplatform.service.LikeService;
import com.kai.videoplatform.service.MQService;
import com.kai.videoplatform.service.RecommendationFeatureService;
import com.kai.videoplatform.service.VideoCacheService;
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
            NotifyMessage notifyMessage = new NotifyMessage("like", video.getAuthorId(), videoId, null);
            notifyMessage.setBizKey("notify:user:" + video.getAuthorId() + ":like:" + videoId + ":actor:" + userId);
            mqService.sendNotify(notifyMessage);
        }
        SearchSyncMessage searchSyncMessage = new SearchSyncMessage("video", videoId, "update");
        searchSyncMessage.setBizKey("search:video:" + videoId + ":update");
        mqService.sendSearchSync(searchSyncMessage);
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