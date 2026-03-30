package com.bilibili.video.model.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageNotifyMessage {
    private Long receiverId;
    private String type; // message / notification
    private Long refId;
    private String content;
}
