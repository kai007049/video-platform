package com.bilibili.video.client.dto;

import lombok.Data;

/**
 * 任务结果
 */
@Data
public class TaskResult {
    private String taskId;
    private String status;
    private Object result;
    private String error;
}
