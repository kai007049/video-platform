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
/**
 * 视频服务门面：
 * 对外保留统一 VideoService 接口，内部按查询/写入职责拆分实现。
 */
public class VideoServiceImpl implements VideoService {

    private final VideoQueryService videoQueryService;
    private final VideoCommandService videoCommandService;

    /**
     * 上传视频
     * @param videoFile 视频文件
     * @param coverFile 封面文件
     * @param dto 上传参数
     * @param authorId 作者ID
     * @return 视频展示对象
     */
    @Override
    public VideoVO upload(MultipartFile videoFile, MultipartFile coverFile, VideoUploadDTO dto, Long authorId) {
        return videoCommandService.upload(videoFile, coverFile, dto, authorId);
    }

    /**
     * 视频列表
     */
    @Override
    public IPage<VideoVO> list(int page, int size, Long userId) {
        return videoQueryService.list(page, size, userId);
    }

    /**
     * 推荐视频列表
     */
    @Override
    public IPage<VideoVO> listRecommended(int page, int size, Long userId) {
        return videoQueryService.listRecommended(page, size, userId);
    }

    /**
     * 热门视频列表
     */
    @Override
    public IPage<VideoVO> listHot(int page, int size, Long userId) {
        return videoQueryService.listHot(page, size, userId);
    }

    /**
     * 按作者查询视频
     */
    @Override
    public IPage<VideoVO> listByAuthor(Long authorId, int page, int size, Long currentUserId) {
        return videoQueryService.listByAuthor(authorId, page, size, currentUserId);
    }

    /**
     * 创作者视频列表
     */
    @Override
    public IPage<VideoVO> listCreatorVideos(Long userId, int page, int size) {
        return videoQueryService.listCreatorVideos(userId, page, size);
    }

    /**
     * 点赞视频列表
     */
    @Override
    public IPage<VideoVO> listLikedVideos(Long userId, int page, int size) {
        return videoQueryService.listLikedVideos(userId, page, size);
    }

    /**
     * 收藏视频列表
     */
    @Override
    public IPage<VideoVO> listFavoriteVideos(Long userId, int page, int size) {
        return videoQueryService.listFavoriteVideos(userId, page, size);
    }

    /**
     * 历史观看列表
     */
    @Override
    public IPage<VideoVO> listHistoryVideos(Long userId, int page, int size) {
        return videoQueryService.listHistoryVideos(userId, page, size);
    }

    /**
     * 查询视频详情
     */
    @Override
    public VideoVO getById(Long videoId, Long userId) {
        return videoQueryService.getById(videoId, userId);
    }

    /**
     * 查询视频详情（基础信息）
     */
    @Override
    public VideoVO getVideoById(Long id) {
        return videoQueryService.getVideoById(id);
    }

    /**
     * 更新视频
     */
    @Override
    public void updateVideo(Video video) {
        videoCommandService.updateVideo(video);
    }

    /**
     * 记录播放量
     */
    @Override
    public void recordPlayCount(Long videoId) {
        videoCommandService.recordPlayCount(videoId);
    }

    /**
     * 设置推荐状态
     */
    @Override
    public void setRecommended(Long videoId, boolean recommended) {
        videoCommandService.setRecommended(videoId, recommended);
    }

    /**
     * 删除视频
     */
    @Override
    public void deleteVideo(Long videoId, Long userId) {
        videoCommandService.deleteVideo(videoId, userId);
    }
}
