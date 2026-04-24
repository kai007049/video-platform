package com.kai.videoplatform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kai.videoplatform.model.vo.SearchUserVO;
import com.kai.videoplatform.model.vo.VideoVO;

import java.util.List;

public interface SearchService {

    IPage<VideoVO> searchVideos(String keyword, int page, int size, String sortBy);

    List<SearchUserVO> searchUsers(String keyword, int page, int size);

    void indexVideo(Long videoId);

    void deleteVideo(Long videoId);

    /**
     * 全量重建视频搜索索引。
     *
     * @return 成功重建的文档数量
     */
    int reindexAllVideos();

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

    /**
     * 混合搜索接口保留给前端调用，当前实现为 ES-only。
     */
    IPage<VideoVO> hybridSearch(String keyword, int page, int size, Long userId);
}