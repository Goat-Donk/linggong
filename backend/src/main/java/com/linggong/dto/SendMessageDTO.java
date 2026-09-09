package com.linggong.dto;

import lombok.Data;

/**
 * 发送聊天消息的请求体。
 *
 * <p>peerId 为对方用户 id：工人端传雇主 id，雇主端传工人 id。
 */
@Data
public class SendMessageDTO {

    /** 岗位 id */
    private Long jobId;

    /** 对方用户 id */
    private Long peerId;

    /** 消息内容（纯文本） */
    private String content;
}
