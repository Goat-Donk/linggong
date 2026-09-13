package com.linggong.config;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 大模型 Bean 装配：OpenAI 兼容协议的模型（默认阿里云百炼 DashScope 的 deepseek-v3）非流式 + 流式模型。
 *
 * <p>为什么不用 langchain4j 自动装配：自动装配在 {@code langchain4j.open-ai.chat-model.api-key}
 * 未配置时整个跳过、且空白 ${DEEPSEEK_API_KEY:} 可能在启动期触发校验失败。
 * 这里显式声明 bean（名字固定 openAiChatModel / openAiStreamingChatModel，@AiService 按名装配），
 * Key 为空时用哨兵值 "EMPTY_KEY" 让应用照常启动，调用时才由业务层 fail-soft
 * （学 MapServiceImpl 对空 Key 的兜底思路）。配置统一走 linggong.ai.*，单一来源。
 * <p>为什么显式指定 httpClientBuilder：classpath 同时存在 langchain4j-http-client-jdk（由
 * langchain4j-open-ai 带入）和 langchain4j-http-client-spring-restclient（由
 * langchain4j-open-ai-spring-boot-starter 带入）两个实现，不指定时 ServiceLoader 报
 * "Conflict: multiple HTTP clients" 启动失败。这里固定用 JDK 自带实现，零额外依赖。
 */
@Configuration
public class AiModelConfig {

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;

    public AiModelConfig(@Value("${linggong.ai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
                         @Value("${linggong.ai.api-key:}") String apiKey,
                         @Value("${linggong.ai.model-name:deepseek-v3}") String modelName) {
        this.baseUrl = baseUrl;
        this.apiKey = StrUtil.blankToDefault(apiKey, "EMPTY_KEY");
        this.modelName = modelName;
    }

    @Bean
    public OpenAiChatModel openAiChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(30))
                .httpClientBuilder(new JdkHttpClientBuilder())
                .build();
    }

    @Bean
    public OpenAiStreamingChatModel openAiStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(30))
                .httpClientBuilder(new JdkHttpClientBuilder())
                .build();
    }
}
