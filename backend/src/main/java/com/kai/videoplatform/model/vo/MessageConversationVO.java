package com.kai.videoplatform.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "消息会话VO")
public class MessageConversationVO {
    @Schema(description = "目标用户ID")
    private Long targetId;
    @Schema(description = "目标用户名称")
    private String targetName;
    @Schema(description = "目标用户头像")
    private String targetAvatar;
    @Schema(description = "最后一条消息内容")
    private String lastContent;
    @Schema(description = "未读消息数")
    private Integer unread;
    @Schema(description = "最后消息时间")
    private LocalDateTime lastTime;
}