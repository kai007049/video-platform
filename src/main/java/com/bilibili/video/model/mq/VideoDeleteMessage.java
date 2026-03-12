package com.bilibili.video.model.mq;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoDeleteMessage {
    @Schema(description = "视频ID")
    private Long videoId;
    @Schema(description = "视频URL")
    private String videoUrl;
    @Schema(description = "封面对象名称")
    private String coverObject;
}
