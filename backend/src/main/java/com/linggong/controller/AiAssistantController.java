package com.linggong.controller;

import cn.hutool.core.util.StrUtil;
import com.linggong.annotation.RateLimiter;
import com.linggong.dto.UserDTO;
import com.linggong.service.AiAssistant;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI 问答助手接口（SSE 流式）。
 *
 * <p>返回 {@code text/plain;charset=utf-8} 的流，前端 fetch + ReadableStream 逐字渲染。
 * memoryId 由服务端用当前登录用户推导（不信客户端参数），会话记忆按人隔离。
 * 空 Key / 流内异常 → 输出兜底话术后结束（保流，不中断）。
 * 注意：限流 / 未登录的拦截结果仍是 Result JSON（非流），前端按 content-type 分流处理。
 */
@Slf4j
@Tag(name = "AI 问答助手")
@RestController
@RequestMapping("/ai/assistant")
public class AiAssistantController {

    private final AiAssistant aiAssistant;
    private final String apiKey;
    private final String fallbackReply;

    public AiAssistantController(AiAssistant aiAssistant,
                                 @Value("${linggong.ai.api-key:}") String apiKey,
                                 @Value("${linggong.ai.fallback-reply:AI 服务暂时不可用，请稍后再试～}") String fallbackReply) {
        this.aiAssistant = aiAssistant;
        this.apiKey = apiKey;
        this.fallbackReply = fallbackReply;
    }

    @Operation(summary = "AI 问答助手流式对话（登录后可用）")
    @RateLimiter(window = 10, limit = 5, type = RateLimiter.LimitType.USER, message = "提问过于频繁，请稍后再试")
    @GetMapping(value = "/chat", produces = "text/plain;charset=utf-8")
    public Flux<String> chat(@RequestParam("message") String message) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Flux.just("请先登录");
        }
        if (StrUtil.isBlank(apiKey)) {
            return Flux.just(fallbackReply);
        }
        String memoryId = RedisConstants.AI_QA_MEMORY_KEY + user.getId();
        String role = user.getRole() == null ? "0" : String.valueOf(user.getRole());
        String userId = String.valueOf(user.getId());
        return aiAssistant.chat(memoryId, message, role, userId)
                // 流内异常（网络/模型返回异常）兜底保流，不把错误抛给前端
                .onErrorResume(e -> {
                    log.error("AI 问答流式调用失败, userId={}, message={}", userId, message, e);
                    return Flux.just(fallbackReply);
                });
    }
}
