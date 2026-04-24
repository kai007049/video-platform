package com.kai.videoplatform.model.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DanmuMessage extends BaseMqMessage {
    private Long videoId;
    private Long userId;
    private String content;
    private Integer timePoint;
}