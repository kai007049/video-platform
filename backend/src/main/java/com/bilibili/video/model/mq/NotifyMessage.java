package com.bilibili.video.model.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotifyMessage {
    private String type;
    private Long userId;
    private Long targetId;
    private String content;
}
