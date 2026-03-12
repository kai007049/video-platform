package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.entity.VideoLike;
import com.bilibili.video.common.Constants;
import com.bilibili.video.exception.BizException;
import com.bilibili.video.mapper.VideoLikeMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.bilibili.video.service.LikeService;
import com.bilibili.video.service.MQService;
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

    private static final String LIKE_KEY = "video:like:";
    private static final long LIKE_EXPIRE_DAYS = 7;

    private final VideoLikeMapper videoLikeMapper;
    private final VideoMapper videoMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MQService mqService;

    @Override
    public void like(Long videoId, Long userId) {
        if (videoMapper.selectById(videoId) == null) {
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
        videoLikeMapper.incrementLikeCount(videoId, 1);

        mqService.sendNotify(new NotifyMessage("like", userId, videoId, "点赞视频"));
        mqService.sendSearchSync(new SearchSyncMessage("video", videoId, "update"));

        redisTemplate.opsForZSet().incrementScore(
                Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h",
                String.valueOf(videoId),
                Constants.HOT_WEIGHT_LIKE
        );
        redisTemplate.expire(Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h", Constants.HOT_WINDOW_HOURS, TimeUnit.HOURS);

        String key = LIKE_KEY + videoId + ":" + userId;
        redisTemplate.opsForValue().set(key, "1", LIKE_EXPIRE_DAYS, TimeUnit.DAYS);
    }

    @Override
    public void unlike(Long videoId, Long userId) {
        int deleted = videoLikeMapper.delete(new LambdaQueryWrapper<VideoLike>()
                .eq(VideoLike::getVideoId, videoId)
                .eq(VideoLike::getUserId, userId));
        if (deleted > 0) {
            videoLikeMapper.incrementLikeCount(videoId, -1);
        }

        String key = LIKE_KEY + videoId + ":" + userId;
        redisTemplate.delete(key);
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
