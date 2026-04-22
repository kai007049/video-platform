package com.bilibili.video.model.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NotifyMessage extends BaseMqMessage {
    private String type;
    private Long userId;
    private Long targetId;
    private String content;
}
