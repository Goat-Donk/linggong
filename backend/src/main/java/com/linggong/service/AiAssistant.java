package com.linggong.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

/**
 * 平台 AI 问答助手（「小灵」）。
 *
 * <p>一个完整的 langchain4j Agent 装配示例：
 * <ul>
 *   <li>{@code tools}：函数调用（Agent）—— 只读工具实时查个人业务数据，返回 JSON 供 LLM 转述；</li>
 *   <li>{@code chatMemoryProvider}：Redis 会话记忆，按 memoryId（用户 id）隔离，最多 20 条；</li>
 *   <li>{@code rules}：平台规则<b>全量</b>注入（见 {@link com.linggong.ai.rule.PlatformRuleBook}）。
 *       规则库只有 19 条，全量塞进提示词比检索召回更准，也不存在漏召；</li>
 *   <li>返回 {@code Flux<String>}：SSE 流式输出，前端逐字渲染。</li>
 * </ul>
 * memoryId 由服务端用 UserHolder 推导（不信客户端），避免跨用户串记忆。
 *
 * <p>注意 {@code @V} 变量会同时渲染进 {@code @SystemMessage(fromResource = ...)} ——
 * 系统提示词里的 {@code {{role}}} / {@code {{userId}}} / {@code {{rules}}} 都靠它填。
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        tools = {"walletTool", "applicationTool", "settlementTool", "jobTool", "attendanceTool"}
)
public interface AiAssistant {

    @SystemMessage(fromResource = "ai/assistant-system.txt")
    Flux<String> chat(@MemoryId String memoryId,
                      @UserMessage String message,
                      @V("role") String role,
                      @V("userId") String userId,
                      @V("rules") String rules);
}
