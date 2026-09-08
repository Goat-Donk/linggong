-- ============================================================
-- 本地零工平台 建表脚本
-- 数据库：linggong（由 docker-compose 的 MYSQL_DATABASE 自动创建）
-- 字符集：utf8mb4，引擎：InnoDB
-- 说明：报名记录（订单）id 用雪花算法生成，其余表 id 自增
-- ============================================================

USE linggong;

-- ---------- 1. 用户表 ----------
CREATE TABLE IF NOT EXISTS `tb_user` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `phone`       varchar(11)  NOT NULL COMMENT '手机号',
    `nick_name`   varchar(32)  NOT NULL DEFAULT '' COMMENT '昵称',
    `icon`        varchar(255) NOT NULL DEFAULT '' COMMENT '头像',
    `role`        tinyint      NOT NULL DEFAULT 0 COMMENT '角色：0打工人 1雇主',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ---------- 2. 用户资料表（与 tb_user 一对一） ----------
CREATE TABLE IF NOT EXISTS `tb_user_info` (
    `id`        bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`   bigint       NOT NULL COMMENT '关联用户 id',
    `introduce` varchar(255) NOT NULL DEFAULT '' COMMENT '个人简介',
    `age`       int          DEFAULT NULL COMMENT '年龄',
    `gender`    tinyint      DEFAULT NULL COMMENT '性别：0未知 1男 2女',
    `credit`    int          NOT NULL DEFAULT 100 COMMENT '信用分',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户资料表';

-- ---------- 3. 岗位分类表 ----------
CREATE TABLE IF NOT EXISTS `tb_job_category` (
    `id`   bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` varchar(32) NOT NULL COMMENT '分类名称',
    `sort` int         NOT NULL DEFAULT 0 COMMENT '排序（越小越靠前）',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '岗位分类表';

-- ---------- 4. 零工岗位表 ----------
CREATE TABLE IF NOT EXISTS `tb_job` (
    `id`          bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_id` bigint        NOT NULL COMMENT '分类 id',
    `employer_id` bigint        NOT NULL COMMENT '雇主（发布者）id',
    `name`        varchar(64)   NOT NULL COMMENT '岗位名称',
    `address`     varchar(255)  NOT NULL DEFAULT '' COMMENT '地址',
    `x`           double        NOT NULL COMMENT '经度',
    `y`           double        NOT NULL COMMENT '纬度',
    `salary`      int           DEFAULT NULL COMMENT '薪资（元）',
    `headcount`   int           NOT NULL DEFAULT 1 COMMENT '名额',
    `start_time`  datetime      DEFAULT NULL COMMENT '开始时间',
    `end_time`    datetime      DEFAULT NULL COMMENT '结束时间',
    `description` varchar(1024) NOT NULL DEFAULT '' COMMENT '岗位描述',
    `status`      tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0上架 1下架',
    `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category_id`),
    KEY `idx_employer` (`employer_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '零工岗位表';

-- ---------- 5. 报名记录表（订单，id 由 RedisIdWorker 雪花算法生成） ----------
CREATE TABLE IF NOT EXISTS `tb_job_application` (
    `id`          bigint   NOT NULL COMMENT '主键（雪花算法生成，不用自增）',
    `job_id`      bigint   NOT NULL COMMENT '岗位 id',
    `worker_id`   bigint   NOT NULL COMMENT '打工人 id',
    `status`      tinyint  NOT NULL DEFAULT 0 COMMENT '状态：0待确认 1已录用 2已完成 3已取消',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_worker` (`job_id`, `worker_id`),
    KEY `idx_worker` (`worker_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '报名记录表';

-- ---------- 6. 互评表 ----------
CREATE TABLE IF NOT EXISTS `tb_job_evaluation` (
    `id`           bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `job_id`       bigint        NOT NULL COMMENT '岗位 id',
    `from_user_id` bigint        NOT NULL COMMENT '评价人 id',
    `to_user_id`   bigint        NOT NULL COMMENT '被评价人 id',
    `rating`       tinyint       NOT NULL DEFAULT 5 COMMENT '评分 1-5',
    `content`      varchar(1024) NOT NULL DEFAULT '' COMMENT '评价内容',
    `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_job` (`job_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '互评表';

-- ---------- 7. 关注表 ----------
CREATE TABLE IF NOT EXISTS `tb_follow` (
    `id`            bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       bigint NOT NULL COMMENT '关注者 id',
    `follow_user_id` bigint NOT NULL COMMENT '被关注用户 id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_follow` (`user_id`, `follow_user_id`),
    KEY `idx_follow_user` (`follow_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '关注表';

-- ---------- 8. 晒单动态表 ----------
CREATE TABLE IF NOT EXISTS `tb_blog` (
    `id`          bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     bigint        NOT NULL COMMENT '发布用户 id',
    `title`       varchar(64)   NOT NULL DEFAULT '' COMMENT '标题',
    `content`     varchar(2048) NOT NULL DEFAULT '' COMMENT '内容',
    `images`      varchar(2048) NOT NULL DEFAULT '' COMMENT '图片列表（逗号分隔）',
    `liked`       int           NOT NULL DEFAULT 0 COMMENT '点赞数',
    `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '晒单动态表';

-- ---------- 9. 岗位收藏表 ----------
CREATE TABLE IF NOT EXISTS `tb_job_favorite` (
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     bigint   NOT NULL COMMENT '收藏者 id',
    `job_id`      bigint   NOT NULL COMMENT '岗位 id',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_job` (`user_id`, `job_id`),
    KEY `idx_job` (`job_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '岗位收藏表';

-- ---------- 10. 求职登记表（打工人简历，与 tb_user 一对一） ----------
CREATE TABLE IF NOT EXISTS `tb_worker_profile` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      bigint       NOT NULL COMMENT '关联打工人用户 id',
    `title`        varchar(64)  NOT NULL DEFAULT '' COMMENT '求职意向一句话',
    `category_ids` varchar(128) NOT NULL DEFAULT '' COMMENT '期望岗位分类 id（逗号分隔，可多选）',
    `skill_tags`   varchar(255) NOT NULL DEFAULT '' COMMENT '技能标签（逗号分隔）',
    `salary_min`   int          DEFAULT NULL COMMENT '期望日薪下限（元，可空）',
    `salary_max`   int          DEFAULT NULL COMMENT '期望日薪上限（元，可空）',
    `work_time`    varchar(128) NOT NULL DEFAULT '' COMMENT '可出勤时段（逗号分隔，可多选）',
    `location`     varchar(255) NOT NULL DEFAULT '' COMMENT '常驻区域 / 可到岗地点',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '求职登记表';

-- ============================================================
-- 种子数据（可选，方便后续开发测试）
-- ============================================================
INSERT INTO `tb_job_category` (`name`, `sort`) VALUES
    ('发传单', 1),
    ('家教辅导', 2),
    ('搬运工', 3),
    ('促销导购', 4),
    ('客服', 5),
    ('保洁', 6);
