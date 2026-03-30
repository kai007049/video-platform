package com.bilibili.video.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bilibili.video.entity.Video;
import com.bilibili.video.model.dto.VideoUploadDTO;
import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private final VideoQueryService videoQueryService;
    private final VideoCommandService videoCommandService;

    @Override
    public VideoVO upload(MultipartFile videoFile, MultipartFile coverFile, VideoUploadDTO dto, Long authorId) {
        return videoCommandService.upload(videoFile, coverFile, dto, authorId);
    }

    @Override
    public IPage<VideoVO> list(int page, int size, Long userId) {
        return videoQueryService.list(page, size, userId);
    }

    @Override
    public IPage<VideoVO> listRecommended(int page, int size, Long userId) {
        return videoQueryService.listRecommended(page, size, userId);
    }

    @Override
    public IPage<VideoVO> listHot(int page, int size, Long userId) {
        return videoQueryService.listHot(page, size, userId);
    }

    @Override
    public IPage<VideoVO> listByAuthor(Long authorId, int page, int size, Long currentUserId) {
        return videoQueryService.listByAuthor(authorId, page, size, currentUserId);
    }

    @Override
    public IPage<VideoVO> listCreatorVideos(Long userId, int page, int size) {
        return videoQueryService.listCreatorVideos(userId, page, size);
    }

    @Override
    public IPage<VideoVO> listLikedVideos(Long userId, int page, int size) {
        return videoQueryService.listLikedVideos(userId, page, size);
    }

    @Override
    public IPage<VideoVO> listFavoriteVideos(Long userId, int page, int size) {
        return videoQueryService.listFavoriteVideos(userId, page, size);
    }

    @Override
    public IPage<VideoVO> listHistoryVideos(Long userId, int page, int size) {
        return videoQueryService.listHistoryVideos(userId, page, size);
    }

    @Override
    public VideoVO getById(Long videoId, Long userId) {
        return videoQueryService.getById(videoId, userId);
    }

    @Override
    public VideoVO getVideoById(Long id) {
        return videoQueryService.getVideoById(id);
    }

    @Override
    public void updateVideo(Video video) {
        videoCommandService.updateVideo(video);
    }

    @Override
    public void recordPlayCount(Long videoId) {
        videoCommandService.recordPlayCount(videoId);
    }

    @Override
    public void setRecommended(Long videoId, boolean recommended) {
        videoCommandService.setRecommended(videoId, recommended);
    }

    @Override
    public void deleteVideo(Long videoId, Long userId) {
        videoCommandService.deleteVideo(videoId, userId);
    }
}
