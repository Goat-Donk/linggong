package com.linggong.utils;

import cn.hutool.core.util.StrUtil;
import com.linggong.dto.UserDTO;

/**
 * AI 工具线程的登录用户还原助手。
 *
 * <p>langchain4j 流式问答的 Agent 工具在非请求线程上执行（SSE 回调线程），
 * 请求线程的 {@link UserHolder} ThreadLocal 在这里拿不到用户。
 * 约定：AiAssistantController 以 {@code ai:qa:memory:{userId}} 作为 memoryId
 * （服务端用登录用户推导、不信客户端），工具通过 {@code @ToolMemoryId} 拿到 memoryId 后，
 * 用本类反解出 userId 并重新建立 UserHolder 上下文，从而复用现有只读 service/mapper 查询。
 */
public final class AiContextHelper {

    private AiContextHelper() {
    }

    /**
     * 从 memoryId 反解 userId；无法识别时返回 null。
     */
    public static Long userIdFromMemoryId(String memoryId) {
        if (StrUtil.isBlank(memoryId) || !memoryId.startsWith(RedisConstants.AI_QA_MEMORY_KEY)) {
            return null;
        }
        String suffix = memoryId.substring(RedisConstants.AI_QA_MEMORY_KEY.length());
        if (StrUtil.isBlank(suffix)) {
            return null;
        }
        try {
            return Long.valueOf(suffix);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 memoryId 构造最小 UserDTO（仅 id，工具查询只需要 userId）。
     */
    public static UserDTO userFromMemoryId(String memoryId) {
        Long id = userIdFromMemoryId(memoryId);
        if (id == null) {
            return null;
        }
        UserDTO user = new UserDTO();
        user.setId(id);
        return user;
    }
}
