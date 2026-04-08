package com.bilibili.video.service;

import java.util.List;

/**
 * 构建轻量级用户画像上下文，用于推荐和 Agent 召回。
 */
public interface UserProfileSummaryService {

    /**
     * 生成用于向量/Agent 检索的用户画像摘要字符串。
     */
    String buildProfileSummary(Long userId);

    /**
     * 获取用户最近观看的视频 ID 列表，用于召回过滤。
     */
    List<Long> listRecentWatchedVideoIds(Long userId, int limit);
}
