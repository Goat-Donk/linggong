package com.linggong.dto;

import lombok.Data;

/**
 * 聊天消息返回体。
 *
 * <p>{@code fromUserId} 用于前端区分消息气泡左右（自己=右，对方=左）。
 */
@Data
public class ChatMessageDTO {

    /** 消息 id */
    private Long id;

    /** 发送者 id */
    private Long fromUserId;

    /** 消息内容 */
    private String content;

    /** 是否已读：0 未读 / 1 已读 */
    private Integer readFlag;

    /** 发送时间（yyyy-MM-dd HH:mm:ss） */
    private String createTime;
}
