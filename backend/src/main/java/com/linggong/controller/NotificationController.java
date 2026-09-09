package com.linggong.controller;

import com.linggong.dto.Result;
import com.linggong.service.INotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知接口：通知列表 / 未读数 / 标记已读。
 */
@Tag(name = "通知接口", description = "站内通知列表 / 未读数 / 标记已读")
@RestController
@RequestMapping("/notification")
public class NotificationController {

    private final INotificationService notificationService;

    public NotificationController(INotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "我的通知列表（分页，按时间倒序）")
    @GetMapping("/list")
    public Result list(@Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                       @Parameter(description = "每页条数，默认 10") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return notificationService.list(page, pageSize);
    }

    @Operation(summary = "未读通知数")
    @GetMapping("/unread-count")
    public Result unreadCount() {
        return notificationService.unreadCount();
    }

    @Operation(summary = "标记全部已读")
    @PutMapping("/read-all")
    public Result markAllRead() {
        return notificationService.markAllRead();
    }
}
