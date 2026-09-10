package com.linggong.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * langchain4j 通用装配：AI 问答助手的会话记忆。
 *
 * <p>记忆按 memoryId（当前登录用户 id）隔离，每个人一个 {@link MessageWindowChatMemory}，
 * 最多保留 20 条消息（约 10 轮对话），超出自动丢最旧；消息序列化后落在 Redis（RedisChatMemoryStore）。
 * 为什么 @AiService 用 chatMemoryProvider 而不是单例 chatMemory：多用户共用一个窗口会串对话，
 * provider 按 memoryId 现取现建，天然隔离。
 */
@Configuration
public class CommonConfig {

    private final ChatMemoryStore redisChatMemoryStore;

    public CommonConfig(ChatMemoryStore redisChatMemoryStore) {
        this.redisChatMemoryStore = redisChatMemoryStore;
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .maxMessages(20)
                .id(memoryId)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }
}
