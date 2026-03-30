package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.model.dto.CommentDTO;
import com.bilibili.video.model.vo.CommentVO;
import com.bilibili.video.common.Constants;
import com.bilibili.video.common.RedisConstants;
import com.bilibili.video.entity.Comment;
import com.bilibili.video.entity.User;
import com.bilibili.video.exception.BizException;
import com.bilibili.video.mapper.CommentMapper;
import com.bilibili.video.mapper.UserMapper;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.model.mq.SearchSyncMessage;
import com.bilibili.video.service.CommentService;
import com.bilibili.video.service.MQService;
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

    @Override
    public CommentVO add(CommentDTO dto, Long userId) {
        if (videoMapper.selectById(dto.getVideoId()) == null) {
            throw new BizException(404, "视频不存在");
        }

        Comment comment = new Comment();
        comment.setVideoId(dto.getVideoId());
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setParentId(dto.getParentId());
        commentMapper.insert(comment);

        String statsKey = RedisConstants.VIDEO_STATS_KEY_PREFIX + dto.getVideoId();
        redisTemplate.opsForHash().increment(statsKey, RedisConstants.VIDEO_STAT_COMMENT, 1);
        redisTemplate.expire(statsKey, RedisConstants.VIDEO_STATS_EXPIRE_DAYS, RedisConstants.DEFAULT_TIME_UNIT_DAYS);

        String commentCacheKey = RedisConstants.COMMENT_LIST_KEY_PREFIX + dto.getVideoId();
        redisTemplate.delete(commentCacheKey);

        mqService.sendNotify(new NotifyMessage("comment", userId, dto.getVideoId(), comment.getContent()));
        mqService.sendSearchSync(new SearchSyncMessage("comment", comment.getId(), "create"));
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
