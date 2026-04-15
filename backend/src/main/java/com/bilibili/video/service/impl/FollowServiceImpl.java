package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.entity.Follow;
import com.bilibili.video.entity.User;
import com.bilibili.video.exception.BizException;
import com.bilibili.video.mapper.FollowMapper;
import com.bilibili.video.mapper.UserMapper;
import com.bilibili.video.model.mq.NotifyMessage;
import com.bilibili.video.model.vo.FollowUserVO;
import com.bilibili.video.service.FollowService;
import com.bilibili.video.service.MQService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final MQService mqService;

    @Override
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new BizException(400, "不能关注自己");
        }
        Follow existing = followMapper.selectOne(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, followerId)
                        .eq(Follow::getFollowingId, followingId));
        if (existing == null) {
            Follow f = new Follow();
            f.setFollowerId(followerId);
            f.setFollowingId(followingId);
            followMapper.insert(f);
            mqService.sendNotify(new NotifyMessage("follow", followingId, followerId, null));
        }
    }

    @Override
    public void unfollow(Long followerId, Long followingId) {
        followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFollowingId, followingId));
    }

    @Override
    public boolean isFollowing(Long followerId, Long followingId) {
        if (followerId == null) return false;
        return followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFollowingId, followingId)) > 0;
    }

    @Override
    public long getFollowingCount(Long userId) {
        return followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, userId));
    }

    @Override
    public long getFanCount(Long userId) {
        return followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowingId, userId));
    }

    @Override
    public java.util.List<FollowUserVO> getFollowingList(Long userId, Long currentUserId) {
        java.util.List<Follow> list = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, userId));
        if (list.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<Long> targetIds = list.stream().map(Follow::getFollowingId).toList();
        return buildUserVoList(targetIds, currentUserId);
    }

    @Override
    public java.util.List<FollowUserVO> getFanList(Long userId, Long currentUserId) {
        java.util.List<Follow> list = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowingId, userId));
        if (list.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<Long> targetIds = list.stream().map(Follow::getFollowerId).toList();
        return buildUserVoList(targetIds, currentUserId);
    }

    private java.util.List<FollowUserVO> buildUserVoList(java.util.List<Long> userIds, Long currentUserId) {
        java.util.List<User> users = userMapper.selectBatchIds(userIds);
        java.util.Set<Long> followedSet = java.util.Collections.emptySet();
        if (currentUserId != null && !users.isEmpty()) {
            java.util.List<Follow> myFollows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getFollowerId, currentUserId)
                    .in(Follow::getFollowingId, userIds));
            followedSet = myFollows.stream().map(Follow::getFollowingId).collect(java.util.stream.Collectors.toSet());
        }
        java.util.Set<Long> finalFollowedSet = followedSet;
        return users.stream().map(u -> {
            FollowUserVO vo = new FollowUserVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setAvatar(u.getAvatar());
            vo.setFollowed(currentUserId != null && finalFollowedSet.contains(u.getId()));
            return vo;
        }).toList();
    }
}
