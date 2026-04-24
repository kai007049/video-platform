package com.kai.videoplatform.service;

import com.kai.videoplatform.model.vo.MessageSummaryVO;

public interface MessageCenterService {

    MessageSummaryVO summary(Long userId);
}