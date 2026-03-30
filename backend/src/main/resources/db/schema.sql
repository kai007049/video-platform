-- 仿 Bilibili 视频平台数据库建表语句
-- MySQL 8.0+



-- 用户表
create table user
(
    id          bigint auto_increment
        primary key,
    username    varchar(64)                          not null comment '用户名',
    password    varchar(128)                         not null comment '密码(BCrypt加密)',
    avatar      varchar(512)                         null comment '头像URL',
    create_time datetime   default CURRENT_TIMESTAMP not null comment '创建时间',
    is_admin    tinyint(1) default 0                 not null comment '是否为管理员',
    phone       varchar(20)                          null comment '电话号',
    email       varchar(25)                          null comment '邮箱',
    constraint username
        unique (username)
)
    comment '用户表' collate = utf8mb4_unicode_ci;

-- 视频表
create table video
(
    id               bigint auto_increment
        primary key,
    title            varchar(256)                         not null comment '视频标题',
    description      text                                 null comment '视频描述',
    author_id        bigint                               not null comment '作者用户ID',
    cover_url        varchar(512)                         null comment '封面图URL',
    video_url        varchar(512)                         not null comment '视频文件URL',
    play_count       bigint     default 0                 not null comment '播放量',
    like_count       bigint     default 0                 not null comment '点赞数',
    create_time      datetime   default CURRENT_TIMESTAMP not null comment '创建时间',
    preview_url      varchar(512)                         null comment '预览动图URL',
    duration_seconds int        default 0                 not null comment '视频时长(秒)',
    is_recommended   tinyint(1) default 0                 not null comment '是否推荐',
    save_count       bigint     default 0                 not null comment '收藏数',
    category_id      bigint     default 0                 not null comment '种类id'
)
    comment '视频表' collate = utf8mb4_unicode_ci;

create index idx_video_author
    on video (author_id);

create index idx_video_create_time
    on video (create_time);

-- 评论表
create table comment
(
    id          bigint auto_increment
        primary key,
    video_id    bigint                             not null comment '视频ID',
    user_id     bigint                             not null comment '评论用户ID',
    content     varchar(1024)                      not null comment '评论内容',
    parent_id   bigint                             null comment '父评论ID(回复时使用)',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    like_count  bigint   default 0                 not null comment '评论点赞数'
)
    comment '评论表' collate = utf8mb4_unicode_ci;

create index idx_comment_parent
    on comment (parent_id);

create index idx_comment_video
    on comment (video_id);

-- 视频点赞表
create table video_like
(
    id          bigint auto_increment
        primary key,
    video_id    bigint                             not null comment '视频ID',
    user_id     bigint                             not null comment '用户ID',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint uk_video_user
        unique (video_id, user_id)
)
    comment '视频点赞表' collate = utf8mb4_unicode_ci;

create index idx_video_like_video
    on video_like (video_id);

-- 弹幕表
create table danmu
(
    id          bigint auto_increment
        primary key,
    video_id    bigint                             not null comment '视频ID',
    user_id     bigint                             not null comment '用户ID',
    content     varchar(256)                       not null comment '弹幕内容',
    time_point  int      default 0                 not null comment '弹幕出现时间点(秒)',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment '弹幕表' collate = utf8mb4_unicode_ci;

create index idx_danmu_video
    on danmu (video_id);

-- 私信表
create table message
(
    id               bigint auto_increment comment '消息ID'
        primary key,
    sender_id        bigint                             not null comment '发送者ID',
    receiver_id      bigint                             not null comment '接收者ID',
    content          varchar(1000)                      not null comment '消息内容',
    status           tinyint  default 0                 null comment '消息状态：0未读 1已读',
    create_time      datetime default CURRENT_TIMESTAMP null comment '创建时间',
    sender_deleted   tinyint  default 0                 null comment '发送方是否删除',
    receiver_deleted tinyint  default 0                 null comment '接收方是否删除'
)
    comment '用户私信表';

-- 通知表
create table notification
(
    id          bigint auto_increment comment '通知ID'
        primary key,
    user_id     bigint                             not null comment '用户ID',
    type        varchar(50)                        not null comment '通知类型',
    content     varchar(1000)                      not null comment '通知内容',
    status      tinyint  default 0                 null comment '通知状态：0未读 1已读',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '系统通知表';

-- ========== V2 扩展：推荐、历史、关注、收藏、UP主、Admin ==========

-- 观看历史
create table watch_history
(
    id            bigint auto_increment
        primary key,
    user_id       bigint                             not null,
    video_id      bigint                             not null,
    watch_seconds int      default 0                 not null,
    create_time   datetime default CURRENT_TIMESTAMP not null,
    update_time   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint uk_user_video
        unique (user_id, video_id)
)
    collate = utf8mb4_unicode_ci;

create index idx_watch_time
    on watch_history (update_time);

create index idx_watch_user
    on watch_history (user_id);

-- 关注
create table follow
(
    id           bigint auto_increment
        primary key,
    follower_id  bigint                             not null,
    following_id bigint                             not null,
    create_time  datetime default CURRENT_TIMESTAMP not null,
    constraint uk_follow
        unique (follower_id, following_id)
)
    comment '用户关注表' collate = utf8mb4_unicode_ci;

create index idx_follow_follower
    on follow (follower_id);

create index idx_follow_following
    on follow (following_id);

-- 收藏
create table favorite
(
    id          bigint auto_increment
        primary key,
    user_id     bigint                             not null,
    video_id    bigint                             not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    constraint uk_fav
        unique (user_id, video_id)
)
    comment '视频收藏表' collate = utf8mb4_unicode_ci;

create index idx_fav_user
    on favorite (user_id);

-- 标签
create table tag
(
    id          bigint auto_increment
        primary key,
    name        varchar(50)                         not null comment '标签名称',
    create_time datetime   default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint uk_tag_name
        unique (name)
)
    comment '标签表' collate = utf8mb4_unicode_ci;

-- 视频-标签关联
create table video_tag
(
    id          bigint auto_increment
        primary key,
    video_id    bigint                             not null comment '视频ID',
    tag_id      bigint                             not null comment '标签ID',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint uk_video_tag
        unique (video_id, tag_id)
)
    comment '视频标签关联表' collate = utf8mb4_unicode_ci;

create index idx_video_tag_video
    on video_tag (video_id);

create index idx_video_tag_tag
    on video_tag (tag_id);

-- 标签初始化数据
insert into tag (name) values
('动画'),
('影视'),
('音乐'),
('游戏'),
('科技'),
('生活'),
('娱乐'),
('搞笑'),
('鬼畜'),
('知识'),
('影视解说'),
('美食'),
('旅行'),
('数码'),
('二次元'),
('Vlog');


