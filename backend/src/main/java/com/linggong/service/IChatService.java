package com.linggong.service;

import com.linggong.dto.Result;

/**
 * 站内沟通（聊天）服务接口。
 *
 * <p>会话粒度：一个岗位 × 一名工人一条会话，雇主由岗位发布者决定。
 * 无需报名即可聊——工人看到岗位即可联系雇主，雇主可联系自己岗位的任一报名工人。
 */
public interface IChatService {

    /**
     * 定位会话：根据岗位 + 对方定位当前用户与对方之间的会话，不存在则返回 null（不自动建）。
     *
     * @param jobId  岗位 id
     * @param peerId 对方用户 id（工人端传雇主 id，雇主端传工人 id）
     */
    Result conversation(Long jobId, Long peerId);

    /**
     * 当前用户的会话列表（按最后消息时间倒序，含对方信息与未读数）。
     */
    Result conversations();

    /**
     * 某会话的消息列表（分页，按时间倒序）。拉取即把对方发来的未读消息置为已读。
     */
    Result messages(Long conversationId, Integer page, Integer pageSize);

    /**
     * 发送一条消息；会话不存在则自动创建。返回会话 id。
     */
    Result send(Long jobId, Long peerId, String content);

    /**
     * 当前用户的未读消息总数。
     */
    Result unreadCount();
}
