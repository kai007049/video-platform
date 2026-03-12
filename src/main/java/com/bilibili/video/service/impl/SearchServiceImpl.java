package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    @Override
    public IPage<VideoVO> searchVideos(String keyword, int page, int size) {
        return new Page<>(page, size, 0);
    }

    @Override
    public void indexVideo(Long videoId) {
        // 暂不处理
    }

    @Override
    public void deleteVideo(Long videoId) {
        // 暂不处理
    }
}
