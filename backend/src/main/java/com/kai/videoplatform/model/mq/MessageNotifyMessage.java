package com.kai.videoplatform.model.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MessageNotifyMessage extends BaseMqMessage {
    private Long receiverId;
    private String type; // message / notification
    private Long refId;
    private String content;
}