package com.linggong.controller;

import cn.hutool.core.util.StrUtil;
import com.linggong.dto.Result;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 连通性冒烟接口：验证 langchain4j + DeepSeek 配置可用。
 *
 * <p>未配置 Key / 调用异常一律返回 Result.fail（fail-soft），不抛错。
 * /ai 前缀不在 MvcConfig 白名单，接口默认要求登录。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class AiSmokeController {

    private final String apiKey;
    private final OpenAiChatModel openAiChatModel;

    public AiSmokeController(@Value("${linggong.ai.api-key:}") String apiKey,
                             OpenAiChatModel openAiChatModel) {
        this.apiKey = apiKey;
        this.openAiChatModel = openAiChatModel;
    }

    @GetMapping("/ping")
    public Result ping() {
        if (StrUtil.isBlank(apiKey)) {
            return Result.fail("未配置 DEEPSEEK_API_KEY");
        }
        try {
            String reply = openAiChatModel.chat("请只回复「AI 连通正常」六个字，不要其它内容");
            return Result.ok(reply);
        } catch (Exception e) {
            log.error("AI 连通性冒烟调用失败", e);
            return Result.fail("AI 服务调用失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage()));
        }
    }
}
