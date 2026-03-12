package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.common.Constants;
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
            videoMapper.incrementSaveCount(videoId, 1L);
            mqService.sendNotify(new NotifyMessage("favorite", userId, videoId, "收藏视频"));
            mqService.sendSearchSync(new SearchSyncMessage("video", videoId, "update"));
            redisTemplate.opsForZSet().incrementScore(
                    Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h",
                    String.valueOf(videoId),
                    Constants.HOT_WEIGHT_FAVORITE
            );
            redisTemplate.expire(Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h", Constants.HOT_WINDOW_HOURS, java.util.concurrent.TimeUnit.HOURS);
        }
    }

    @Override
    @Transactional
    public void remove(Long userId, Long videoId) {
        int deleted = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getVideoId, videoId));
        if (deleted > 0) {
            videoMapper.incrementSaveCount(videoId, -1L);
        }
    }

    @Override
    public boolean isFavorited(Long userId, Long videoId) {
        if (userId == null) return false;
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getVideoId, videoId)) > 0;
    }
}
