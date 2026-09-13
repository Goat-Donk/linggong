-- ============================================================
-- 聊天功能演示数据（幂等可重跑：先清理再插入）
-- 账号：工人101 李同学(13900000004) / 工人102 张阿姨 / 工人103 刘小哥 / 雇主51(13800000001)
-- ============================================================
USE linggong;
SET NAMES utf8mb4;

-- 清理聊天测试数据（聊天表为全新，直接清空不影响其他业务）
DELETE FROM tb_chat_message;
DELETE FROM tb_chat_conversation;

-- 会话 1：job 203 展会促销兼职 × 工人101 李同学 × 雇主51（有来有回）
INSERT INTO tb_chat_conversation (id, job_id, worker_id, employer_id, last_message, last_message_time, create_time, update_time) VALUES
(1, 203, 101, 51, '好的，报名吧，我这边通过', '2026-09-09 10:30:00', '2026-09-09 10:20:00', '2026-09-09 10:30:00');

-- 会话 2：job 202 仓库装卸 × 工人102 张阿姨 × 雇主51
INSERT INTO tb_chat_conversation (id, job_id, worker_id, employer_id, last_message, last_message_time, create_time, update_time) VALUES
(2, 202, 102, 51, '仓库装卸还要人不？', '2026-09-09 09:45:00', '2026-09-09 09:45:00', '2026-09-09 09:45:00');

-- 会话 3：job 201 小学数学家教 × 工人103 刘小哥 × 雇主51
INSERT INTO tb_chat_conversation (id, job_id, worker_id, employer_id, last_message, last_message_time, create_time, update_time) VALUES
(3, 201, 103, 51, '您好，家教这个我可以', '2026-09-09 09:00:00', '2026-09-09 09:00:00', '2026-09-09 09:00:00');

-- 会话 1 消息（李同学 ↔ 雇主；最后一条雇主发给李同学，read_flag=0 = 李同学有 1 条未读）
INSERT INTO tb_chat_message (conversation_id, from_user_id, to_user_id, content, read_flag, create_time) VALUES
(1, 101, 51, '你好，请问展会促销还招人吗？', 1, '2026-09-09 10:20:00'),
(1, 51, 101, '招的，9月18号一天，你有促销经验吗？', 1, '2026-09-09 10:22:00'),
(1, 101, 51, '有的，之前做过车展促销', 1, '2026-09-09 10:25:00'),
(1, 51, 101, '好的，报名吧，我这边通过', 0, '2026-09-09 10:30:00');

-- 会话 2 消息（张阿姨问雇主，read_flag=0 = 雇主未读）
INSERT INTO tb_chat_message (conversation_id, from_user_id, to_user_id, content, read_flag, create_time) VALUES
(2, 102, 51, '仓库装卸还要人不？', 0, '2026-09-09 09:45:00');

-- 会话 3 消息（刘小哥问雇主，read_flag=0 = 雇主未读）
INSERT INTO tb_chat_message (conversation_id, from_user_id, to_user_id, content, read_flag, create_time) VALUES
(3, 103, 51, '您好，家教这个我可以', 0, '2026-09-09 09:00:00');
