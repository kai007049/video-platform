package com.bilibili.video.model.mq;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public abstract class BaseMqMessage {
    private String eventId;
    private String bizKey;
    private String traceId;
    private String eventType;
    private Integer version;
    private LocalDateTime occurredAt;
}
