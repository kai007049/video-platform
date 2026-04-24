package com.kai.videoplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kai.videoplatform.model.dto.CommentDTO;
import com.kai.videoplatform.model.vo.CommentVO;
import com.kai.videoplatform.common.Constants;
import com.kai.videoplatform.common.RedisConstants;
import com.kai.videoplatform.entity.Comment;
import com.kai.videoplatform.entity.User;
import com.kai.videoplatform.exception.BizException;
import com.kai.videoplatform.mapper.CommentMapper;
import com.kai.videoplatform.mapper.UserMapper;
import com.kai.videoplatform.mapper.VideoMapper;
import com.kai.videoplatform.model.mq.NotifyMessage;
import com.kai.videoplatform.model.mq.SearchSyncMessage;
import com.kai.videoplatform.service.CommentService;
import com.kai.videoplatform.service.MQService;
import com.kai.videoplatform.service.RecommendationFeatureService;
import com.kai.videoplatform.service.VideoCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论服务实现
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final VideoMapper videoMapper;
    private final MQService mqService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RecommendationFeatureService recommendationFeatureService;
    private final VideoCacheService videoCacheService;

    @Override
    public CommentVO add(CommentDTO dto, Long userId) {
        var video = videoMapper.selectById(dto.getVideoId());
        if (video == null) {
            throw new BizException(404, "视频不存在");
        }

        Comment comment = new Comment();
        comment.setVideoId(dto.getVideoId());
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setParentId(dto.getParentId());
        commentMapper.insert(comment);

        // 评论数统一以评论表聚合结果为准，这里不再额外维护 Redis comment delta，避免统计口径分裂。
        String commentCacheKey = RedisConstants.COMMENT_LIST_KEY_PREFIX + dto.getVideoId();
        redisTemplate.delete(commentCacheKey);
        // 评论新增会影响视频详情页里的 commentCount，需要同步失效聚合详情缓存。
        videoCacheService.invalidateVideo(dto.getVideoId());

        if (video.getAuthorId() != null && !video.getAuthorId().equals(userId)) {
            NotifyMessage notifyMessage = new NotifyMessage("comment", video.getAuthorId(), dto.getVideoId(), comment.getContent());
            notifyMessage.setBizKey("notify:user:" + video.getAuthorId() + ":comment:" + comment.getId());
            mqService.sendNotify(notifyMessage);
        }
        SearchSyncMessage searchSyncMessage = new SearchSyncMessage("comment", comment.getId(), "create");
        searchSyncMessage.setBizKey("search:comment:" + comment.getId() + ":create");
        mqService.sendSearchSync(searchSyncMessage);
        recommendationFeatureService.increaseUserInterestByVideo(userId, dto.getVideoId(), 2.5D);
        redisTemplate.opsForZSet().incrementScore(
                Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h",
                String.valueOf(dto.getVideoId()),
                Constants.HOT_WEIGHT_COMMENT
        );
        redisTemplate.expire(Constants.HOT_RANK_PREFIX + Constants.HOT_WINDOW_HOURS + "h", Constants.HOT_WINDOW_HOURS, java.util.concurrent.TimeUnit.HOURS);

        User user = userMapper.selectById(userId);
        CommentVO vo = toCommentVO(comment, user);
        vo.setReplies(new ArrayList<>());
        return vo;
    }

    @Override
    public List<CommentVO> listByVideoId(Long videoId) {
        String cacheKey = RedisConstants.COMMENT_LIST_KEY_PREFIX + videoId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<CommentVO> cachedList = (List<CommentVO>) cached;
            return cachedList;
        }

        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getVideoId, videoId)
                        .orderByAsc(Comment::getCreateTime));

        if (comments.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, new ArrayList<>(), RedisConstants.COMMENT_LIST_NULL_TTL);
            return new ArrayList<>();
        }

        List<Long> userIds = comments.stream().map(Comment::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, List<Comment>> parentMap = comments.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Comment::getParentId));

        List<CommentVO> result = comments.stream()
                .filter(c -> c.getParentId() == null)
                .map(c -> buildCommentVO(c, userMap, parentMap))
                .collect(Collectors.toList());

        redisTemplate.opsForValue().set(cacheKey, result, RedisConstants.COMMENT_LIST_TTL);
        return result;
    }

    private CommentVO buildCommentVO(Comment comment, Map<Long, User> userMap, Map<Long, List<Comment>> parentMap) {
        CommentVO vo = toCommentVO(comment, userMap.get(comment.getUserId()));
        List<Comment> replies = parentMap.getOrDefault(comment.getId(), new ArrayList<>());
        vo.setReplies(replies.stream()
                .map(r -> toCommentVO(r, userMap.get(r.getUserId())))
                .collect(Collectors.toList()));
        return vo;
    }

    private CommentVO toCommentVO(Comment comment, User user) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setVideoId(comment.getVideoId());
        vo.setUserId(comment.getUserId());
        vo.setContent(comment.getContent());
        vo.setParentId(comment.getParentId());
        vo.setCreateTime(comment.getCreateTime());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setUserAvatar(user.getAvatar());
        }
        return vo;
    }
}