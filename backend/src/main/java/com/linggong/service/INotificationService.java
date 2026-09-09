package com.linggong.service;

import com.linggong.dto.Result;

/**
 * 站内通知服务接口。
 *
 * <p>业务动作（审核、结算）产生通知，用户在前端查看 / 标记已读。
 */
public interface INotificationService {

    /**
     * 发送一条站内通知。
     *
     * @param userId  接收人 id
     * @param type    通知类型（见 {@link com.linggong.entity.Notification} 的 TYPE_* 常量）
     * @param title   标题
     * @param content 内容
     * @param bizId   关联业务 id（岗位 id，可为 null）
     */
    void notify(Long userId, String type, String title, String content, Long bizId);

    /**
     * 查询当前用户的通知列表（分页，按时间倒序）。
     */
    Result list(Integer page, Integer pageSize);

    /**
     * 当前用户未读通知数。
     */
    Result unreadCount();

    /**
     * 把当前用户的所有通知标记为已读。
     */
    Result markAllRead();
}
