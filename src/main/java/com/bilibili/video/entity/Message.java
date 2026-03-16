package com.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long senderId;
    private Long receiverId;
    private String content;
    /** 0 未读 / 1 已读 */
    private Integer status;
    /** 0 未删除 / 1 已删除（发送方视角） */
    private Integer senderDeleted;
    /** 0 未删除 / 1 已删除（接收方视角） */
    private Integer receiverDeleted;
    private LocalDateTime createTime;
}
