package com.bilibili.video.model.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频展示 VO
 */
@Data
@Schema(description = "视频展示 VO")
public class VideoVO {

    @TableId(type = IdType.AUTO)
    private Long id;
    @Schema(description = "标题")
    private String title;
    @Schema(description = "描述")
    private String description;
    @Schema(description = "作者 ID")
    private Long authorId;
    @Schema(description = "作者名称")
    private String authorName;
    @Schema(description = "作者头像")
    private String authorAvatar;
    @Schema(description = "封面地址")
    private String coverUrl;
    @Schema(description = "预览动图地址")
    private String previewUrl;
    @Schema(description = "视频地址")
    private String videoUrl;
    @Schema(description = "视频时长(秒)")
    private Integer durationSeconds;
    @Schema(description = "是否推荐")
    private Boolean isRecommended;
    /** 播放地址，指向后端转发接口，解决 MinIO 私有桶浏览器无法直接访问问题 */
    @Schema(description = "播放地址")
    private String playUrl;
    @Schema(description = "播放次数")
    private Long playCount;
    @Schema(description = "点赞次数")
    private Long likeCount;
    @Schema(description = "评论次数")
    private Long commentCount;
    @Schema(description = "收藏次数")
    private Long saveCount;
    @Schema(description = "是否点赞")
    private Boolean liked;
    @Schema(description = "是否收藏")
    private Boolean favorited;
    /** 上次观看进度(秒)，用于继续播放 */
    private Integer lastWatchSeconds;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
