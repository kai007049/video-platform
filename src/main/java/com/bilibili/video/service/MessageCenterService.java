package com.bilibili.video.service;

import com.bilibili.video.model.vo.MessageSummaryVO;

public interface MessageCenterService {

    MessageSummaryVO summary(Long userId);
}
