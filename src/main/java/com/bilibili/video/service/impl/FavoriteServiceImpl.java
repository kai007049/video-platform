package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.common.Constants;
import com.bilibili.video.common.RedisConstants;
import com.bilibili.video.entity.Favorite;
import com.bilibili.video.mapper.FavoriteMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.bilibili.video.service.FavoriteService;
import com.bilibili.video.service.MQService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private static final String FAVORITE_KEY_PREFIX = RedisConstants.VIDEO_FAVORITE_KEY_PREFIX;
    private static final long FAVORITE_EXPIRE_DAYS = RedisConstants.VIDEO_FAVORITE_EXPIRE_DAYS;

    private final FavoriteMapper favoriteMapper;

    private final VideoMapper videoMapper;
    private final MQService mqService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public void add(Long userId, Long videoId) {
        Favorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getVideoId, videoId));
        if (existing == null) {
            Favorite f = new Favorite();
            f.setUserId(userId);
            f.setVideoId(videoId);
            favoriteMapper.insert(f);

            String statsKey = RedisConstants.VIDEO_STATS_KEY_PREFIX + videoId;
            redisTemplate.opsForHash().increment(statsKey, RedisConstants.VIDEO_STAT_SAVE, 1);
            redisTemplate.expire(statsKey, RedisConstants.VIDEO_STATS_EXPIRE_DAYS, RedisConstants.DEFAULT_TIME_UNIT_DAYS);

            mqService.sendNotify(new NotifyMessage("favorite", userId, videoId, "收藏视频"));
            mqService.sendSearchSync(new SearchSyncMessage("video", videoId, "update"));
            redisTemplate.opsForZSet().incrementScore(
                    Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h",
                    String.valueOf(videoId),
                    Constants.HOT_WEIGHT_FAVORITE
            );
            redisTemplate.expire(Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h", Constants.HOT_WINDOW_HOURS, java.util.concurrent.TimeUnit.HOURS);

            String key = FAVORITE_KEY_PREFIX + videoId + ":" + userId;
            redisTemplate.opsForValue().set(key, "1", FAVORITE_EXPIRE_DAYS, java.util.concurrent.TimeUnit.DAYS);
        }
    }

    @Override
    @Transactional
    public void remove(Long userId, Long videoId) {
        int deleted = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getVideoId, videoId));
        if (deleted > 0) {
            String statsKey = RedisConstants.VIDEO_STATS_KEY_PREFIX + videoId;
            redisTemplate.opsForHash().increment(statsKey, RedisConstants.VIDEO_STAT_SAVE, -1);
            redisTemplate.expire(statsKey, RedisConstants.VIDEO_STATS_EXPIRE_DAYS, RedisConstants.DEFAULT_TIME_UNIT_DAYS);
        }

        String key = FAVORITE_KEY_PREFIX + videoId + ":" + userId;
        redisTemplate.delete(key);
    }

    @Override
    public boolean isFavorited(Long userId, Long videoId) {
        if (userId == null) return false;
        String key = FAVORITE_KEY_PREFIX + videoId + ":" + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return true;
        }
        long count = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getVideoId, videoId));
        if (count > 0) {
            redisTemplate.opsForValue().set(key, "1", FAVORITE_EXPIRE_DAYS, java.util.concurrent.TimeUnit.DAYS);
            return true;
        }
        return false;
    }
}
