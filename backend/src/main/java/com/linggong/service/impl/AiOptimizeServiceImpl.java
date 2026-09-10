package com.linggong.service.impl;

import cn.hutool.core.util.StrUtil;
import com.linggong.dto.AiOptimizeRequest;
import com.linggong.dto.Result;
import com.linggong.dto.UserDTO;
import com.linggong.service.IAiOptimizeService;
import com.linggong.utils.UserHolder;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

/**
 * 岗位描述 AI 优化实现。
 *
 * <p>调用链：AiController → optimizeJobDescription → 角色守卫（仅雇主）→ 拼 ChatRequest
 * （system 提示词 + 表单事实）→ DeepSeek 非流式返回 → 截断 1024 → Result.ok。
 * 空 Key / LLM 异常一律 fail-soft 返回友好提示，不把堆栈抛给前端（学 AiSkillServiceImpl 的 fallback 思路）。
 */
@Slf4j
@Service
public class AiOptimizeServiceImpl implements IAiOptimizeService {

    private static final int MAX_OUTPUT_LENGTH = 1024;

    private final String apiKey;
    private final String fallbackReply;
    private final ChatModel chatModel;
    private String systemPrompt;

    public AiOptimizeServiceImpl(@Value("${linggong.ai.api-key:}") String apiKey,
                                 @Value("${linggong.ai.fallback-reply:AI 服务暂时不可用，请稍后再试～}") String fallbackReply,
                                 ChatModel chatModel) {
        this.apiKey = apiKey;
        this.fallbackReply = fallbackReply;
        this.chatModel = chatModel;
    }

    @PostConstruct
    public void loadPrompt() {
        try {
            systemPrompt = new ClassPathResource("ai/job-optimize-system.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("加载岗位优化提示词失败，使用内置兜底", e);
            systemPrompt = "你是本地零工平台的岗位发布助手。根据雇主填写的表单信息，把岗位描述写得清楚专业：只使用表单里真实存在的事实数字，绝不编造；口语转书面；按【工作内容】【任职要求】【待遇与结算】【工作地点】组织；不超过 1024 字。";
        }
    }

    @Override
    public Result optimizeJobDescription(AiOptimizeRequest request) {
        // 1. 角色守卫：仅雇主（role=1）可用
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getRole() == null || user.getRole() != 1) {
            return Result.fail("只有雇主可以发布岗位");
        }
        // 2. 空 Key fail-soft
        if (StrUtil.isBlank(apiKey)) {
            return Result.fail("未配置 DEEPSEEK_API_KEY");
        }
        // 3. 拼 prompt 调模型
        try {
            ChatResponse response = chatModel.chat(ChatRequest.builder()
                    .messages(SystemMessage.from(systemPrompt),
                            UserMessage.from(renderUserMessage(request)))
                    .build());
            String text = response.aiMessage() == null ? null : response.aiMessage().text();
            if (StrUtil.isBlank(text)) {
                return Result.fail(fallbackReply);
            }
            return Result.ok(text.length() > MAX_OUTPUT_LENGTH
                    ? text.substring(0, MAX_OUTPUT_LENGTH)
                    : text);
        } catch (Exception e) {
            log.warn("岗位描述 AI 优化失败，返回兜底提示。name={}", request.getName(), e);
            return Result.fail(fallbackReply);
        }
    }

    /**
     * 把表单字段渲染成给 LLM 的用户消息：先给事实清单，再给草稿（草稿空则只给事实）。
     */
    private String renderUserMessage(AiOptimizeRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("岗位表单信息：\n")
                .append("岗位名称：").append(nvl(request.getName())).append('\n')
                .append("岗位分类：").append(nvl(request.getCategoryName())).append('\n')
                .append("日薪：").append(request.getSalary() == null ? "未填" : request.getSalary() + " 元/天").append('\n')
                .append("招聘名额：").append(request.getHeadcount() == null ? "未填" : request.getHeadcount() + " 人").append('\n')
                .append("工作时间：").append(buildTimeText(request)).append('\n')
                .append("工作地址：").append(nvl(request.getAddress())).append('\n');
        if (StrUtil.isNotBlank(request.getDescription())) {
            sb.append("\n雇主原始草稿：\n").append(request.getDescription());
        } else {
            sb.append("\n（雇主未填写草稿，请根据以上表单信息从零生成岗位描述）");
        }
        return sb.toString();
    }

    private String buildTimeText(AiOptimizeRequest request) {
        if (StrUtil.isBlank(request.getStartTime()) && StrUtil.isBlank(request.getEndTime())) {
            return "未填";
        }
        return nvl(request.getStartTime()) + " 至 " + nvl(request.getEndTime());
    }

    private String nvl(String value) {
        return StrUtil.isBlank(value) ? "未填" : value.trim();
    }
}
