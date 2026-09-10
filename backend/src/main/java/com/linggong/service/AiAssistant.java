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
 *   <li>{@code contentRetriever}：RAG 检索增强 —— 平台规则知识库（Bm25ContentRetriever，BM25 关键词检索）；</li>
 *   <li>{@code tools}：函数调用（Agent）—— 5 个只读工具实时查个人业务数据，返回 JSON 供 LLM 转述；</li>
 *   <li>{@code chatMemoryProvider}：Redis 会话记忆，按 memoryId（用户 id）隔离，最多 20 条；</li>
 *   <li>返回 {@code Flux<String>}：SSE 流式输出，前端逐字渲染。</li>
 * </ul>
 * memoryId 由服务端用 UserHolder 推导（不信客户端），避免跨用户串记忆。
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        contentRetriever = "ruleBm25Retriever",
        tools = {"walletTool", "applicationTool", "settlementTool", "jobTool", "attendanceTool"}
)
public interface AiAssistant {

    @SystemMessage(fromResource = "ai/assistant-system.txt")
    Flux<String> chat(@MemoryId String memoryId,
                      @UserMessage String message,
                      @V("role") String role,
                      @V("userId") String userId);
}
