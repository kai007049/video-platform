package com.bilibili.video.service;

import com.bilibili.video.model.dto.AccountLoginDTO;
import com.bilibili.video.model.dto.UserRegisterDTO;
import com.bilibili.video.model.vo.CreatorStatsVO;
import com.bilibili.video.model.vo.LoginVO;
import com.bilibili.video.model.vo.UpProfileVO;
import com.bilibili.video.model.vo.UserInfoVO;

public interface UserService {

    void register(UserRegisterDTO dto);

    LoginVO login(AccountLoginDTO dto);

    UserInfoVO getUserInfo(Long userId);

    UpProfileVO getUpProfile(Long upId, Long currentUserId);

    CreatorStatsVO getCreatorStats(Long userId);
}
