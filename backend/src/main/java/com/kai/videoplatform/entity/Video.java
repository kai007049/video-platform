package com.kai.videoplatform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 瑙嗛瀹炰綋
 */
@Data
@TableName("video")
public class Video {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "鏍囬")
    private String title;

    @Schema(description = "鎻忚堪")
    private String description;

    @Schema(description = "浣滆€匢D")
    private Long authorId;

    @Schema(description = "灏侀潰URL")
    private String coverUrl;

    @Schema(description = "棰勮URL")
    private String previewUrl;

    @Schema(description = "瑙嗛URL")
    private String videoUrl;

    @Schema(description = "鎾斁娆℃暟")
    private Long playCount;

    @Schema(description = "鐐硅禐娆℃暟")
    private Long likeCount;

    @Schema(description = "鏀惰棌娆℃暟")
    private Long saveCount;

    @Schema(description = "瑙嗛鏃堕暱(绉?)")
    private Integer durationSeconds;

    @Schema(description = "鏄惁鎺ㄨ崘")
    private Boolean isRecommended;

    @Schema(description = "鍒嗙被ID")
    private Long categoryId;

    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Boolean deleted;

    @Schema(description = "逻辑删除时间")
    private LocalDateTime deleteTime;

    @Schema(description = "鍒涘缓鏃堕棿")
    private LocalDateTime createTime;
}
