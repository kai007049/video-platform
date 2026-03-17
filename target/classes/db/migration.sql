-- 在已有数据库上执行此脚本以添加新功能
-- 若 column 已存在会报错，可忽略或注释对应行

-- video 表扩展
ALTER TABLE `video` ADD COLUMN `duration_seconds` INT NOT NULL DEFAULT 0 COMMENT '视频时长(秒)' AFTER `like_count`;
ALTER TABLE `video` ADD COLUMN `preview_url` VARCHAR(512) DEFAULT NULL COMMENT '预览动图URL' AFTER `cover_url`;
ALTER TABLE `video` ADD COLUMN `is_recommended` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否推荐' AFTER `duration_seconds`;

-- user 表扩展
ALTER TABLE `user` ADD COLUMN `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱' AFTER `avatar`;
ALTER TABLE `user` ADD COLUMN `phone` VARCHAR(32) DEFAULT NULL COMMENT '手机号' AFTER `email`;
ALTER TABLE `user` ADD COLUMN `is_admin` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否管理员' AFTER `phone`;

-- 若之前添加过 OAuth 字段，可执行以下删除（不存在会报错，可忽略）
ALTER TABLE `user` DROP COLUMN `oauth_provider`;
ALTER TABLE `user` DROP COLUMN `oauth_id`;
ALTER TABLE `user` DROP COLUMN `oauth_union_id`;
