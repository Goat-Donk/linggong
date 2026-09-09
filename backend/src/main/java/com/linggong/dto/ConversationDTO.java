package com.linggong.dto;

import lombok.Data;

/**
 * 会话列表项 / 会话定位返回体。
 *
 * <p>{@code peerId / peerName / peerIcon} 是「对方」信息（相对当前登录用户），
 * 前端据此展示会话列表头像昵称、跳转聊天窗口。
 */
@Data
public class ConversationDTO {

    /** 会话 id */
    private Long id;

    /** 岗位 id */
    private Long jobId;

    /** 对方用户 id */
    private Long peerId;

    /** 对方昵称 */
    private String peerName;

    /** 对方头像 */
    private String peerIcon;

    /** 最后一条消息预览 */
    private String lastMessage;

    /** 最后消息时间（yyyy-MM-dd HH:mm:ss） */
    private String lastMessageTime;

    /** 当前用户在此会话的未读数 */
    private Long unreadCount;
}
