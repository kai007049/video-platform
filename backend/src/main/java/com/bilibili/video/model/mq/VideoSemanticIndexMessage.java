package com.bilibili.video.model.mq;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoSemanticIndexMessage {
    @Schema(description = "视频ID")
    private Long videoId;
    @Schema(description = "视频流地址")
    private String videoUrl;
    @Schema(description = "封面对象名称")
    private String coverUrl;
    @Schema(description = "标题")
    private String title;
    @Schema(description = "简介")
    private String description;
    @Schema(description = "分类ID")
    private Long categoryId;
    @Schema(description = "标签名称列表")
    private List<String> tags = new ArrayList<>();
}
