package com.linggong.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linggong.dto.Result;
import com.linggong.entity.Notification;
import com.linggong.mapper.NotificationMapper;
import com.linggong.service.INotificationService;
import com.linggong.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * 站内通知服务实现。
 *
 * <p>查询 / 标记已读都限定在当前登录用户范围内，防止越权访问他人通知。
 */
@Service
public class NotificationServiceImpl implements INotificationService {

    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Override
    public void notify(Long userId, String type, String title, String content, Long bizId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setBizId(bizId);
        n.setReadFlag(0);
        notificationMapper.insert(n);
    }

    @Override
    public Result list(Integer page, Integer pageSize) {
        Long userId = UserHolder.getUser().getId();
        Page<Notification> p = notificationMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime));
        return Result.ok(p.getRecords(), p.getTotal());
    }

    @Override
    public Result unreadCount() {
        Long userId = UserHolder.getUser().getId();
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0));
        return Result.ok(count);
    }

    @Override
    public Result markAllRead() {
        Long userId = UserHolder.getUser().getId();
        Notification upd = new Notification();
        upd.setReadFlag(1);
        notificationMapper.update(upd, new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0));
        return Result.ok();
    }
}
