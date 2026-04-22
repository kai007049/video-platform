package com.bilibili.video.model.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VideoProcessMessage extends BaseMqMessage {
    private Long videoId;
    private Long userId;
}
