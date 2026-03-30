package com.bilibili.video.search;

import com.bilibili.video.entity.Video;

public interface VideoSearchService {

    void index(Video video);

    void delete(Long videoId);
}
