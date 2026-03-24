package com.bilibili.video.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bilibili.video.model.vo.SearchUserVO;
import com.bilibili.video.model.vo.VideoVO;

import java.util.List;

public interface SearchService {

    IPage<VideoVO> searchVideos(String keyword, int page, int size);

    List<SearchUserVO> searchUsers(String keyword, int page, int size);

    void indexVideo(Long videoId);

    void deleteVideo(Long videoId);

    /**
     * 记录搜索词（写入用户历史 + 热搜计数）
     */
    void recordSearchKeyword(Long userId, String keyword);

    /**
     * 获取当前用户搜索历史（最近在前）
     */
    List<String> getSearchHistory(Long userId, int limit);

    /**
     * 清空当前用户搜索历史
     */
    void clearSearchHistory(Long userId);

    /**
     * 获取热门搜索
     */
    List<String> getHotSearches(int limit);
}
