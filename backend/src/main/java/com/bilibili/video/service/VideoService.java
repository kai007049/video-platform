package com.bilibili.video.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bilibili.video.model.dto.VideoUploadDTO;
import com.bilibili.video.model.vo.VideoVO;
import com.bilibili.video.entity.Video;
import org.springframework.web.multipart.MultipartFile;

/**
 * 视频服务接口
 */
public interface VideoService {

    VideoVO upload(MultipartFile videoFile, MultipartFile coverFile, VideoUploadDTO dto, Long authorId);

    IPage<VideoVO> list(int page, int size, Long userId);

    /** 推荐流：热门+最新混合排序 */
    IPage<VideoVO> listRecommended(int page, int size, Long userId);

    /** 热榜列表 */
    IPage<VideoVO> listHot(int page, int size, Long userId);

    /** 按作者查询（UP主主页） */
    IPage<VideoVO> listByAuthor(Long authorId, int page, int size, Long currentUserId);

    /** 创作者自己的作品列表 */
    IPage<VideoVO> listCreatorVideos(Long userId, int page, int size);

    /** 点赞过的视频列表 */
    IPage<VideoVO> listLikedVideos(Long userId, int page, int size);

    /** 收藏过的视频列表 */
    IPage<VideoVO> listFavoriteVideos(Long userId, int page, int size);

    /** 历史观看的视频列表 */
    IPage<VideoVO> listHistoryVideos(Long userId, int page, int size);

    VideoVO getById(Long videoId, Long userId);

    VideoVO getVideoById(Long id);

    void updateVideo(Video video);

    void recordPlayCount(Long videoId);

    void setRecommended(Long videoId, boolean recommended);

    void deleteVideo(Long videoId, Long userId);

}
