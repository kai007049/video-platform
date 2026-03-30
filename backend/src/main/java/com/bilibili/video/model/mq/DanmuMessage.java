package com.bilibili.video.model.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DanmuMessage {
    private Long videoId;
    private Long userId;
    private String content;
    private Integer timePoint;
}
