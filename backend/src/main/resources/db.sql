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
    `break_count` int        NOT NULL DEFAULT 0 COMMENT '放鸽子次数（已录用后工人单方放弃，供雇主审核参考）',
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
-- 一人一单双保险：Redis Lua 原子判重（seckill.lua sismember apply:order）为主，
-- 下方生成列 active_flag + 唯一键 uk_job_worker_active 做 DB 层兜底——
-- 进行中(0待确认/1已录用)=1 仅允许一条；终态(2/3)自动变 NULL，MySQL 唯一索引允许多个 NULL，历史多条不受限。
CREATE TABLE IF NOT EXISTS `tb_job_application` (
    `id`          bigint   NOT NULL COMMENT '主键（雪花算法生成，不用自增）',
    `job_id`      bigint   NOT NULL COMMENT '岗位 id',
    `worker_id`   bigint   NOT NULL COMMENT '打工人 id',
    `status`      tinyint  NOT NULL DEFAULT 0 COMMENT '状态：0待确认 1已录用 2已完成 3已取消',
    `active_flag` tinyint  GENERATED ALWAYS AS (IF(`status` IN (0,1), 1, NULL)) STORED COMMENT '进行中标记（生成列，随 status 自动重算）：1=进行中 0/1；NULL=终态 2/3',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_worker_active` (`job_id`, `worker_id`, `active_flag`),
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

-- ---------- 20. 雇主拉黑打工人黑名单（全局生效：拉黑后该雇主所有岗位报名被拦） ----------
CREATE TABLE IF NOT EXISTS `tb_employer_blacklist` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `employer_id` bigint       NOT NULL COMMENT '拉黑发起方（雇主）id',
    `worker_id`   bigint       NOT NULL COMMENT '被打工人 id',
    `reason`      varchar(100) NOT NULL DEFAULT '' COMMENT '拉黑原因（雇主自选/自填，仅雇主侧可见）',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_employer_worker` (`employer_id`, `worker_id`),
    KEY `idx_employer` (`employer_id`),
    KEY `idx_worker` (`worker_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '雇主拉黑打工人黑名单';

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

-- ---------- 21. 平台规则知识库（AI 问答的规则依据） ----------
-- 内容口径与上方各表及业务代码保持一致（结算/信用/考勤/黑名单等），
-- 由 PlatformRuleBook 启动时全量载入并注入系统提示词（规则库小，不做检索）。
CREATE TABLE IF NOT EXISTS `tb_ai_rule` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`       varchar(128) NOT NULL COMMENT '规则标题（注入提示词时的分段标题）',
    `tags`        varchar(255) NOT NULL DEFAULT '' COMMENT '检索标签（逗号分隔，如：结算,工资,服务费）',
    `content`     text         NOT NULL COMMENT '规则正文：可直接引用的口语化完整回答',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '平台规则知识库（AI 问答的规则依据）';

-- 种子规则（口径全部对齐现网业务实现）
INSERT INTO `tb_ai_rule` (`title`, `tags`, `content`) VALUES
('平台是什么、有哪两种身份', '平台,角色,身份,打工人,雇主',
 '小灵是一个本地零工平台：雇主在上面发布按天计薪的零工岗位，打工人报名、按时做工、按实际出勤天数结算工资，平台负责撮合和工资担保。用户分两种身份：打工人（角色0）和雇主（角色1）。'),
('注册默认身份与雇主开通', '注册,身份,雇主,开通',
 '注册新账号默认是打工人身份（角色0）。雇主身份（角色1）需要平台侧开通——演示环境由管理员在后台设置。打工人发不了岗位，雇主也不能报名打工，两边能力是分开的。'),
('发岗流程与担保金托管', '发岗,发布岗位,担保金,冻结,托管',
 '雇主发布岗位时会从钱包可用余额里冻结一笔担保金，金额 = 日薪 × 招聘名额 × 任务天数（起止跨自然日按天数算）。这笔钱不是扣掉，是冻结托管：结算时用来给打工人发工资，剩余的退回雇主钱包。余额不足时不能发布。'),
('编辑岗位补退担保金', '编辑岗位,担保金,补冻,释放,余额不足',
 '编辑已发布岗位时，担保金按新金额多退少补：金额变高了要补冻差额，变低了自动释放差额。可用余额不够补差额时，编辑会被拒绝。'),
('报名状态与一人一岗', '报名,状态,一人一岗,待确认,已录用',
 '打工人报名岗位后进入「待确认」，雇主审核后变「已录用」，结算完成后变「已完成」，被拒绝、撤销或解除则变「已取消」。同一人对同一岗位同时只能有一条进行中的报名（待确认/已录用），只有这一条结束（已完成/已取消）后才能再次报名。'),
('报名审核与名额释放', '审核,录用,拒绝,名额',
 '雇主在「审核报名」里逐条处理：通过=录用（打工人变已录用，准备上岗），拒绝=取消报名并释放该名额，名额释放后其他打工人可以继续报名。'),
('撤销报名', '撤销,取消报名,名额',
 '打工人可以在报名还是「待确认」时自行撤销，撤销后这条报名变「已取消」，名额立刻释放。一旦雇主已经录用（已确认），就不能再自己撤了，只能走「放弃录用」（会扣信用分）。'),
('放弃录用与取消录用（放鸽子）', '放鸽子,放弃,取消录用,信用分,解除',
 '已录用的工人开工前放弃（工人侧「放弃录用」）、或雇主单方取消录用（雇主侧「取消录用」），都属于违约：发起方信用分 -10，同时释放名额。但有一条硬保护：只要该工人对这个岗位已经核销过到岗（到岗核销通过），就不能再解除，只能等结算按实际做工天数付工资，避免白干。'),
('考勤打卡与核销', '考勤,打卡,核销,到岗,下工,半天',
 '每天出工，打工人要打两次卡：到岗、下工。雇主按天核销：两条都核销通过 = 记 1 天；只通过到岗、下工没通过 = 记半天；都没通过 = 不计。打卡记录是发工资的唯一依据。'),
('工资结算口径', '结算,工资,服务费,退款,下架',
 '岗位结束（或雇主手动提前结算）时触发结算：工资 = 日薪 × 累计核销半天数 ÷ 2，按每个工人实际核销天数计算并直接发到工人钱包；结算后报名标记「已完成」、岗位自动下架、不能再核销考勤或编辑岗位。结算只发生一次（一岗一单，幂等）。'),
('服务费由谁承担', '服务费,抽成,10%,结算',
 '平台按结算工资总额收 10% 服务费，这笔钱从雇主的可用余额里另行扣除，不碰工人的工资——工人拿到的就是核销天数的全额工资。如果雇主余额不够付服务费，结算会失败，需要先充值。'),
('钱包充值提现', '钱包,充值,提现,余额',
 '每个用户都有一个虚拟钱包，先充值才能发岗（要冻结担保金）或付服务费。工资结算后自动进钱包可用余额，可以随时提现。所有资金变动（充值/冻结/解冻/工资/服务费）都有一笔流水可查。'),
('信用分规则', '信用分,评分,降权,曝光,clamp',
 '每个人默认 100 信用分，范围 0-100。互评影响：被评 5 星 +2、4 星 +1、3 星不变、1-2 星 -3；放鸽子/被取消录用 -10。信用分低于 60 的雇主，他发布的岗位在默认「最新」列表里会被降权沉底，但用户明确按薪资或距离排序不受影响。'),
('互评规则', '互评,评价,评分,星级',
 '一个岗位结算完成后，参与双方可以互相评价（打工人评雇主、雇主评打工人），评分 1-5 星。同一人对同一岗位只能评价一次。评价结果会联动被评价人的信用分。'),
('雇主黑名单', '黑名单,拉黑,封禁,名额',
 '雇主可以把不守约的打工人拉进黑名单，拉黑后该打工人无法再报名这个雇主的任何岗位，同时系统会自动取消该工人在这个雇主所有岗位下「待确认」的报名并释放名额。单个雇主最多同时拉黑 50 人。'),
('站内通知', '通知,消息,录用,拒绝,工资到账',
 '关键节点会给用户发站内通知：报名被录用、报名被拒绝、结算工资到账等，在「我的-通知」里查看，点通知可跳转到对应岗位。'),
('岗位聊天', '聊天,沟通,联系',
 '雇主和打工人围绕一个岗位一对一沟通，从岗位详情点「联系雇主/联系打工人」进入。会话和岗位绑定，方便双方商量时间、地点等细节。'),
('下架岗位的条件', '下架,停止招聘,在岗',
 '雇主可以下架自己发布的岗位。但如果这个岗位还有录用工人在做工，不能直接下架，必须先结算（结算会自动下架）；已经结算过的岗位无需重复下架。'),
('常见问题：工资多久到账', '常见问题,工资,到账,提现',
 '工资在岗位结算时立即到账钱包（会收到「工资到账」通知），可以马上提现。注意：工资按实际核销的考勤天数计算，不是按报名天数；考勤没过核销的天不计薪。');

-- ---------- 22. AI 问答追踪（运行时 trace） ----------
-- 一次问答写一行。用途有二：① Bad Case 归因（这条回答带了哪些规则依据、各段耗时多少）
-- ② 前端「这条回答的依据是什么」面板的数据源。
-- 刻意拆成结构化列而非一个大 JSON 日志：query / 规则 id 要能直接 where 和 group by，
-- 塞进 JSON blob 里就只能全表捞出来在应用层过滤了。
-- injected_rule_ids 与 latency_breakdown 用 varchar 存 JSON 文本而非原生 JSON 类型：
-- 原生 JSON 列会在写入时校验并报错，而本表的埋点是「失败仅告警、绝不打断问答主流程」，
-- 两者取向冲突；JSON 文本仍可被 JSON_CONTAINS 等函数直接消费，能力不打折。
CREATE TABLE IF NOT EXISTS `tb_ai_trace` (
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `trace_id`           char(32)     NOT NULL COMMENT '追踪 id，一次问答一个（32 位无横线 UUID）',
    `user_id`            bigint       DEFAULT NULL COMMENT '提问人 id（Bad Case 归因要能定位到人）',
    `user_role`          tinyint      DEFAULT NULL COMMENT '提问人角色：0打工人 1雇主（同一问题两种角色答案不同）',
    `query`              varchar(512) NOT NULL COMMENT '用户原始问题',
    `injected_rule_ids`  varchar(255) NOT NULL DEFAULT '[]' COMMENT '本次注入提示词的规则 id，JSON 数组；全量注入模式下为规则库全集',
    `latency_breakdown`  varchar(512) NOT NULL DEFAULT '{}' COMMENT '各段耗时 JSON 对象（毫秒）；未启用的阶段为 null',
    `final_answer`       varchar(2048) DEFAULT NULL COMMENT '最终回答（超长截断，仅用于归因）',
    `status`             varchar(16)  NOT NULL DEFAULT 'OK' COMMENT '结束状态：OK正常 / ERROR流内异常 / INCOMPLETE未正常结束',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_trace_id` (`trace_id`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 问答追踪（RAG 链路 trace）';
