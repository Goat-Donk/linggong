package com.linggong.utils;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AI 问答助手会话记忆的 Redis 存储。
 *
 * <p>langchain4j 的 {@link ChatMemoryStore} 只要求「按 memoryId 存/取/删消息列表」，
 * 这里把消息序列化成 JSON 存 String，key 带 TTL（1 天）自动过期，不占长期内存。
 */
@Repository
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisChatMemoryStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = stringRedisTemplate.opsForValue().get(memoryId.toString());
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        String json = ChatMessageSerializer.messagesToJson(list);
        // key = ai:qa:memory:{userId}，TTL 1 天，保持 Redis 干净
        stringRedisTemplate.opsForValue().set(
                memoryId.toString(), json, RedisConstants.AI_QA_MEMORY_TTL, TimeUnit.DAYS);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        stringRedisTemplate.delete(memoryId.toString());
    }
}
