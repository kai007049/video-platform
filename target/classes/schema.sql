-- 仿 Bilibili 视频平台数据库建表语句
-- MySQL 8.0+



-- 用户表
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(128) NOT NULL COMMENT '密码(BCrypt加密)',
    `avatar` VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    `is_admin` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否管理员',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 视频表
CREATE TABLE `video` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(256) NOT NULL COMMENT '视频标题',
    `description` TEXT COMMENT '视频描述',
    `author_id` BIGINT NOT NULL COMMENT '作者用户ID',
    `cover_url` VARCHAR(512) DEFAULT NULL COMMENT '封面图URL',
    `preview_url` VARCHAR(512) DEFAULT NULL COMMENT '预览动图URL',
    `video_url` VARCHAR(512) NOT NULL COMMENT '视频文件URL',
    `play_count` BIGINT NOT NULL DEFAULT 0 COMMENT '播放量',
    `like_count` BIGINT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `duration_seconds` INT NOT NULL DEFAULT 0 COMMENT '视频时长(秒)',
    `is_recommended` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否推荐',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频表';

CREATE INDEX `idx_video_author` ON `video`(`author_id`);
CREATE INDEX `idx_video_create_time` ON `video`(`create_time`);

-- 评论表
CREATE TABLE `comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `video_id` BIGINT NOT NULL COMMENT '视频ID',
    `user_id` BIGINT NOT NULL COMMENT '评论用户ID',
    `content` VARCHAR(1024) NOT NULL COMMENT '评论内容',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID(回复时使用)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

CREATE INDEX `idx_comment_video` ON `comment`(`video_id`);
CREATE INDEX `idx_comment_parent` ON `comment`(`parent_id`);

-- 视频点赞表
CREATE TABLE `video_like` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `video_id` BIGINT NOT NULL COMMENT '视频ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_video_user` (`video_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频点赞表';

CREATE INDEX `idx_video_like_video` ON `video_like`(`video_id`);

-- 弹幕表
CREATE TABLE `danmu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `video_id` BIGINT NOT NULL COMMENT '视频ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `content` VARCHAR(256) NOT NULL COMMENT '弹幕内容',
    `time_point` INT NOT NULL DEFAULT 0 COMMENT '弹幕出现时间点(秒)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='弹幕表';

CREATE INDEX `idx_danmu_video` ON `danmu`(`video_id`);

-- 私信表
CREATE TABLE `message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `sender_id` BIGINT NOT NULL COMMENT '发送者ID',
    `receiver_id` BIGINT NOT NULL COMMENT '接收者ID',
    `content` VARCHAR(1024) NOT NULL COMMENT '内容',
    `status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_message_sender` (`sender_id`),
    INDEX `idx_message_receiver` (`receiver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信表';

-- 通知表
CREATE TABLE `notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `type` VARCHAR(32) NOT NULL COMMENT '类型',
    `content` VARCHAR(1024) NOT NULL COMMENT '内容',
    `status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_notification_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';

-- ========== V2 扩展：推荐、历史、关注、收藏、UP主、Admin ==========

-- 观看历史
CREATE TABLE IF NOT EXISTS `watch_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `video_id` BIGINT NOT NULL,
    `watch_seconds` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_video` (`user_id`, `video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_watch_user` ON `watch_history`(`user_id`);
CREATE INDEX `idx_watch_time` ON `watch_history`(`update_time`);

-- 关注
CREATE TABLE IF NOT EXISTS `follow` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `follower_id` BIGINT NOT NULL,
    `following_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_follow` (`follower_id`, `following_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_follow_follower` ON `follow`(`follower_id`);
CREATE INDEX `idx_follow_following` ON `follow`(`following_id`);

-- 收藏
CREATE TABLE IF NOT EXISTS `favorite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `video_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_fav` (`user_id`, `video_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_fav_user` ON `favorite`(`user_id`);

CREATE TABLE message (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
                         sender_id BIGINT NOT NULL COMMENT '发送者ID',
                         receiver_id BIGINT NOT NULL COMMENT '接收者ID',
                         content VARCHAR(1000) NOT NULL COMMENT '消息内容',
                         status TINYINT DEFAULT 0 COMMENT '消息状态：0未读 1已读',
                         create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='用户私信表';

CREATE TABLE notification (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '通知ID',
                              user_id BIGINT NOT NULL COMMENT '用户ID',
                              type VARCHAR(50) NOT NULL COMMENT '通知类型',
                              content VARCHAR(1000) NOT NULL COMMENT '通知内容',
                              status TINYINT DEFAULT 0 COMMENT '通知状态：0未读 1已读',
                              create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='系统通知表';

