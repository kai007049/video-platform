package com.bilibili.video.model.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchSyncMessage {
    private String entityType;
    private Long entityId;
    private String action;
}
