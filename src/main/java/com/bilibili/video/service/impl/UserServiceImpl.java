package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bilibili.video.common.Constants;
import com.bilibili.video.model.dto.AccountLoginDTO;
import com.bilibili.video.model.dto.UserRegisterDTO;
import com.bilibili.video.model.vo.CreatorStatsVO;
import com.bilibili.video.model.vo.LoginVO;
import com.bilibili.video.model.vo.UpProfileVO;
import com.bilibili.video.model.vo.UserInfoVO;
import com.bilibili.video.entity.User;
import com.bilibili.video.entity.Video;
import com.bilibili.video.mapper.VideoMapper;
import com.bilibili.video.service.CaptchaService;
import com.bilibili.video.service.FollowService;
import com.bilibili.video.exception.BizException;
import com.bilibili.video.mapper.UserMapper;
import com.bilibili.video.service.UserService;
import com.bilibili.video.utils.JwtUtils;
import com.bilibili.video.utils.MyBCr;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final VideoMapper videoMapper;
    private final FollowService followService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtils jwtUtils;
    private static final String[] DEFAULT_AVATARS = {
            "default/微信图片_20260316115333.jpg",
            "default/微信图片_20260316115347.jpg",
            "default/微信图片_20260316115405.jpg",
            "default/微信图片_20260316115406.jpg",
            "default/微信图片_202603161154061.jpg",
            "default/微信图片_202603161154062.jpg"
    };

    private final CaptchaService captchaService;
    private final com.bilibili.video.utils.MinioUtils minioUtils;

    @Override
    public void register(UserRegisterDTO dto) {
        long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BizException(400, "用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(MyBCr.encode(dto.getPassword())); // 用自己封装的加密
        user.setAvatar(randomDefaultAvatar());
        userMapper.insert(user);
    }

    @Override
    public LoginVO login(AccountLoginDTO dto) {
        captchaService.verifyCaptcha(dto.getCaptchaKey(), dto.getCaptchaValue());
        User user = findUserByAccount(dto.getAccount());
        if (user == null) {
            throw new BizException(404, "账号不存在");
        }
        if (!MyBCr.matches(dto.getPassword().trim(), user.getPassword())) {
            throw new BizException(400, "密码错误");
        }
        return buildLoginResponse(user);
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        return toUserInfoVO(user);
    }

    @Override
    public UpProfileVO getUpProfile(Long upId, Long currentUserId) {
        User user = userMapper.selectById(upId);
        if (user == null) throw new BizException(404, "用户不存在");
        UpProfileVO vo = new UpProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setCreateTime(user.getCreateTime());
        vo.setVideoCount(videoMapper.selectCount(new LambdaQueryWrapper<Video>().eq(Video::getAuthorId, upId)));
        vo.setFanCount(followService.getFanCount(upId));
        vo.setFollowingCount(followService.getFollowingCount(upId));
        vo.setFollowed(currentUserId != null && followService.isFollowing(currentUserId, upId));
        return vo;
    }

    @Override
    public CreatorStatsVO getCreatorStats(Long userId) {
        var videos = videoMapper.selectList(new LambdaQueryWrapper<Video>().eq(Video::getAuthorId, userId));
        long totalPlay = videos.stream().mapToLong(Video::getPlayCount).sum();
        long totalLike = videos.stream().mapToLong(Video::getLikeCount).sum();
        CreatorStatsVO vo = new CreatorStatsVO();
        vo.setTotalPlayCount(totalPlay);
        vo.setTotalLikeCount(totalLike);
        vo.setVideoCount(videos.size());
        vo.setFanCount(followService.getFanCount(userId));
        return vo;
    }

    @Override
    public String updateAvatar(Long userId, org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "头像文件不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        String avatarUrl;
        try {
            avatarUrl = minioUtils.uploadAvatar(file);
        } catch (Exception e) {
            throw new BizException(500, "头像上传失败: " + e.getMessage());
        }
        user.setAvatar(avatarUrl);
        userMapper.updateById(user);
        return avatarUrl;
    }

    private LoginVO buildLoginResponse(User user) {
        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        redisTemplate.opsForValue().set(Constants.loginTokenPrefix + ":" + user.getId(), token, Constants.LOGIN_EXPIRE_MINUTES, TimeUnit.SECONDS);
        return new LoginVO(token, toUserInfoVO(user));
    }

    private User findUserByAccount(String account) {
        String trimmed = account.trim();
        if (trimmed.contains("@")) {
            return userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, trimmed));
        }
        if (trimmed.matches("\\d{6,}")) {
            User byPhone = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, trimmed));
            if (byPhone != null) {
                return byPhone;
            }
        }
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, trimmed));
    }

    private String randomDefaultAvatar() {
        int idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(DEFAULT_AVATARS.length);
        return DEFAULT_AVATARS[idx];
    }

    private UserInfoVO toUserInfoVO(User user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setIsAdmin(user.getIsAdmin() != null && user.getIsAdmin());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}