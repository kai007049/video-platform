package com.bilibili.video.client.dto;

import lombok.Data;

/**
 * 任务创建响应
 */
@Data
public class TaskResponse {
    private String taskId;
    private String status;
}
