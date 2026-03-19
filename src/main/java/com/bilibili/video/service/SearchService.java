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
}
