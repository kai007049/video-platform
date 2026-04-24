package com.kai.videoplatform.service;

import com.kai.videoplatform.model.dto.AccountLoginDTO;
import com.kai.videoplatform.model.dto.UserRegisterDTO;
import com.kai.videoplatform.model.vo.CreatorStatsVO;
import com.kai.videoplatform.model.vo.LoginVO;
import com.kai.videoplatform.model.vo.UpProfileVO;
import com.kai.videoplatform.model.vo.UserInfoVO;

public interface UserService {

    void register(UserRegisterDTO dto);

    LoginVO login(AccountLoginDTO dto);

    UserInfoVO getUserInfo(Long userId);

    UpProfileVO getUpProfile(Long upId, Long currentUserId);

    CreatorStatsVO getCreatorStats(Long userId);

    String updateAvatar(Long userId, org.springframework.web.multipart.MultipartFile file);
}