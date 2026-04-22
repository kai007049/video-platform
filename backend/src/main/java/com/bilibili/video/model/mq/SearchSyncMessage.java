package com.bilibili.video.model.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SearchSyncMessage extends BaseMqMessage {
    private String entityType;
    private Long entityId;
    private String action;
}
