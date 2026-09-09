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
    `salary`      int           DEFAULT NULL COMMENT '日薪（元/天）',
    `headcount`   int           NOT NULL DEFAULT 1 COMMENT '名额',
    `frozen_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '已担保冻结金额（元，精确到分，发岗时冻结=日薪×名额×任务天数）',
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
    KEY `idx_job` (`job_id`),
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
    UNIQUE KEY `uk_job_from` (`job_id`, `from_user_id`),
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

-- ---------- 11. 虚拟钱包表（每人一个，balance=可用余额；担保冻结/工资结算都走这里） ----------
CREATE TABLE IF NOT EXISTS `tb_wallet` (
    `id`          bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     bigint        NOT NULL COMMENT '关联用户 id（雇主/打工人共用）',
    `balance`     decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '可用余额（元，精确到分）',
    `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '虚拟钱包表';

-- ---------- 12. 钱包流水表（充值/冻结/解冻/工资/服务费） ----------
CREATE TABLE IF NOT EXISTS `tb_wallet_log` (
    `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       bigint        NOT NULL COMMENT '关联用户 id（谁的钱变动了）',
    `type`          varchar(32)   NOT NULL COMMENT '类型：充值/冻结/解冻/工资/服务费',
    `amount`        decimal(10,2) NOT NULL COMMENT '变动金额（元，正=入账，负=出账，精确到分）',
    `balance_after` decimal(10,2) NOT NULL COMMENT '变动后余额（元，精确到分）',
    `biz_id`        bigint        DEFAULT NULL COMMENT '关联业务 id（如岗位/结算单），可为空',
    `remark`        varchar(255)  NOT NULL DEFAULT '' COMMENT '备注',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '钱包流水表';

-- ---------- 13. 每日考勤表（打工人按天申请「到岗/下工」，雇主按日核销，构成计薪依据） ----------
CREATE TABLE IF NOT EXISTS `tb_attendance` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `job_id`      bigint      NOT NULL COMMENT '岗位 id',
    `worker_id`   bigint      NOT NULL COMMENT '打工人 id',
    `work_date`   date        NOT NULL COMMENT '出勤日期',
    `on_status`   tinyint     NOT NULL DEFAULT 0 COMMENT '到岗核销状态：0未申请 1待核销 2通过 3驳回',
    `off_status`  tinyint     NOT NULL DEFAULT 0 COMMENT '下工核销状态：0未申请 1待核销 2通过 3驳回',
    `on_time`     datetime    DEFAULT NULL COMMENT '到岗申请时间',
    `off_time`    datetime    DEFAULT NULL COMMENT '下工申请时间',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_worker_date` (`job_id`, `worker_id`, `work_date`),
    KEY `idx_worker` (`worker_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '每日考勤表';

-- ---------- 14. 岗位结算单（job_id 唯一 = 结算幂等锚点；一岗一单，落单即结算完成） ----------
CREATE TABLE IF NOT EXISTS `tb_job_settlement` (
    `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `job_id`        bigint        NOT NULL COMMENT '岗位 id（一岗仅一单）',
    `employer_id`   bigint        NOT NULL COMMENT '雇主 id',
    `trigger_type`  tinyint       NOT NULL DEFAULT 0 COMMENT '触发来源：0手动提前结算 1到期自动结算',
    `gross_wage`    decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '实际发给工人的工资总额（元）',
    `service_fee`   decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '向雇主钱包另扣的平台服务费（元，=工资×10%）',
    `refund_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '退回雇主钱包的冻结余款（元）',
    `settled_at`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '结算完成时间',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job` (`job_id`),
    KEY `idx_employer` (`employer_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '岗位结算单表';

-- ---------- 15. 结算工人明细（逐人快照：出勤半天数 + 实发工资，账务可追溯） ----------
CREATE TABLE IF NOT EXISTS `tb_job_settlement_item` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `settlement_id`   bigint        NOT NULL COMMENT '所属结算单 id',
    `application_id`  bigint        NOT NULL COMMENT '已完成报名记录 id（雪花 id）',
    `worker_id`       bigint        NOT NULL COMMENT '打工人 id',
    `paid_half_days`  int           NOT NULL DEFAULT 0 COMMENT '已付半天数：2=1天 1=半天',
    `wage_amount`     decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '工资金额（元，精确到分）',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_settlement` (`settlement_id`),
    KEY `idx_worker` (`worker_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '结算工人明细表';

-- ---------- 16. 站内通知表（审核结果、结算工资等被动通知） ----------
CREATE TABLE IF NOT EXISTS `tb_notification` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     bigint       NOT NULL COMMENT '接收人 id',
    `type`        varchar(32)  NOT NULL COMMENT '类型：APPLY_APPROVED报名录用 / APPLY_REJECTED报名拒绝 / SETTLE_WAGE工资到账',
    `title`       varchar(128) NOT NULL COMMENT '标题',
    `content`     varchar(512) NOT NULL DEFAULT '' COMMENT '内容',
    `biz_id`      bigint       DEFAULT NULL COMMENT '关联业务 id（岗位 id），用于跳转',
    `read_flag`   tinyint      NOT NULL DEFAULT 0 COMMENT '是否已读：0未读 1已读',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内通知表';

-- ---------- 17. 聊天会话表（雇主↔工人 围绕岗位的一对一对话） ----------
CREATE TABLE IF NOT EXISTS `tb_chat_conversation` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `job_id`            bigint       NOT NULL COMMENT '岗位 id',
    `worker_id`         bigint       NOT NULL COMMENT '打工人 id',
    `employer_id`       bigint       NOT NULL COMMENT '雇主 id（冗余自岗位，便于会话列表查询）',
    `last_message`      varchar(255) DEFAULT NULL COMMENT '最后一条消息预览',
    `last_message_time` datetime     DEFAULT NULL COMMENT '最后消息时间（会话排序）',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_worker` (`job_id`, `worker_id`),
    KEY `idx_worker` (`worker_id`),
    KEY `idx_employer` (`employer_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '聊天会话表';

-- ---------- 18. 聊天消息表 ----------
CREATE TABLE IF NOT EXISTS `tb_chat_message` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `conversation_id` bigint       NOT NULL COMMENT '会话 id',
    `from_user_id`    bigint       NOT NULL COMMENT '发送者 id',
    `to_user_id`      bigint       NOT NULL COMMENT '接收者 id',
    `content`         varchar(500) NOT NULL COMMENT '消息内容（纯文本）',
    `read_flag`       tinyint      NOT NULL DEFAULT 0 COMMENT '是否已读：0未读 1已读',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_conversation` (`conversation_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '聊天消息表';

-- ---------- 19. 信用分流水表（每次信用分变动记一条，供本人追溯 / 审计） ----------
CREATE TABLE IF NOT EXISTS `tb_user_credit_log` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      bigint       NOT NULL COMMENT '信用分变动对象 id',
    `reason_type`  varchar(32)  NOT NULL COMMENT '变动原因：EVALUATION互评 / BREAK_HIRE放鸽子(单方解除)',
    `change_amount` int         NOT NULL COMMENT '实际变动分（正加负减，clamp 后真实差值）',
    `after_credit` int          NOT NULL COMMENT '变动后信用分',
    `biz_id`       bigint       DEFAULT NULL COMMENT '关联业务 id（岗位 id）',
    `remark`       varchar(255) NOT NULL DEFAULT '' COMMENT '备注说明',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '信用分流水表';

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
