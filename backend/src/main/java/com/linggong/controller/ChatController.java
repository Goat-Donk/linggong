package com.linggong.controller;

import com.linggong.dto.Result;
import com.linggong.dto.SendMessageDTO;
import com.linggong.service.IChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内沟通接口：定位会话 / 会话列表 / 消息列表 / 发送消息 / 未读数。
 */
@Tag(name = "聊天接口", description = "雇主↔工人站内沟通")
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final IChatService chatService;

    public ChatController(IChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "定位会话（不存在返回 null，不自动建）")
    @GetMapping("/conversation")
    public Result conversation(@Parameter(description = "岗位 id") @RequestParam Long jobId,
                               @Parameter(description = "对方用户 id") @RequestParam Long peerId) {
        return chatService.conversation(jobId, peerId);
    }

    @Operation(summary = "我的会话列表（按最后消息时间倒序）")
    @GetMapping("/conversations")
    public Result conversations() {
        return chatService.conversations();
    }

    @Operation(summary = "会话消息列表（分页倒序，拉取即已读）")
    @GetMapping("/messages")
    public Result messages(@Parameter(description = "会话 id") @RequestParam Long conversationId,
                           @Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                           @Parameter(description = "每页条数，默认 20") @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return chatService.messages(conversationId, page, pageSize);
    }

    @Operation(summary = "发送消息（会话不存在则自动创建）")
    @PostMapping("/send")
    public Result send(@RequestBody SendMessageDTO dto) {
        return chatService.send(dto.getJobId(), dto.getPeerId(), dto.getContent());
    }

    @Operation(summary = "未读消息总数")
    @GetMapping("/unread-count")
    public Result unreadCount() {
        return chatService.unreadCount();
    }
}
