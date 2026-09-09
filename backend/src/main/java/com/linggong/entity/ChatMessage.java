package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息实体，对应表 tb_chat_message。
 *
 * <p>纯文本消息，归属某个会话；read_flag 表示接收者是否已读，用于未读数统计。
 */
@Data
@TableName("tb_chat_message")
public class ChatMessage {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 id（关联 tb_chat_conversation.id） */
    private Long conversationId;

    /** 发送者 id */
    private Long fromUserId;

    /** 接收者 id */
    private Long toUserId;

    /** 消息内容（纯文本） */
    private String content;

    /** 是否已读：0 未读 / 1 已读 */
    private Integer readFlag;

    /** 创建时间 */
    private LocalDateTime createTime;
}
