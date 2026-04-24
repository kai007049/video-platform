package com.kai.videoplatform.search;

import com.kai.videoplatform.entity.Video;

public interface VideoSearchService {

    void index(Video video);

    void delete(Long videoId);
}