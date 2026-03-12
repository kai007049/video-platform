package com.bilibili.video.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bilibili.video.model.vo.VideoVO;

public interface SearchService {

    IPage<VideoVO> searchVideos(String keyword, int page, int size);

    void indexVideo(Long videoId);

    void deleteVideo(Long videoId);
}
