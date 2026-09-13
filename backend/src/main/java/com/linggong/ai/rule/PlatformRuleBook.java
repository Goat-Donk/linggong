package com.linggong.ai.rule;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.linggong.entity.AiRule;
import com.linggong.mapper.AiRuleMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 平台规则手册：启动时把 {@code tb_ai_rule} 全量载入并拼成一段文本，随系统提示词一起注入。
 *
 * <p><b>为什么是全量注入而不是 RAG：</b>规则库只有 19 条、约 1.6K 字（≈1.2K token），
 * 远低于模型上下文窗口。这个规模下检索是<b>负收益</b>——
 * 一是会漏召（口语化 query 与规则词表不重合时零召回，如「押金压多少」在规则库里只有「担保金」），
 * 二是会在超纲问题上把不相关规则塞进上下文，反而诱导模型硬答。
 * 全量注入的召回率恒为 1，且整层失败模式直接消失。决策依据见 PROGRESS.md「RAG 决策记录」。
 *
 * <p>规则是静态的（改规则需重启，与改造前一致），所以只在 {@link #load()} 时拼一次，
 * 之后每次问答只是把同一个字符串填进提示词模板 —— 同时也让 prompt 前缀稳定，命中服务端上下文缓存。
 */
@Slf4j
@Component
public class PlatformRuleBook {

    private final AiRuleMapper aiRuleMapper;

    /** 全部规则拼成的文本；载入失败时为空串（此时 AI 仍可答个人数据类问题，只是没有规则依据）。 */
    private volatile String text = "";

    /** 已载入的规则 id，供 trace 记录「这次回答握着哪些依据」。 */
    private volatile List<Long> ruleIds = List.of();

    public PlatformRuleBook(AiRuleMapper aiRuleMapper) {
        this.aiRuleMapper = aiRuleMapper;
    }

    @PostConstruct
    public void load() {
        List<AiRule> rules;
        try {
            rules = aiRuleMapper.selectList(Wrappers.<AiRule>lambdaQuery().orderByAsc(AiRule::getId));
        } catch (Exception e) {
            // 与业务代码里「埋点失败仅告警」同一思路：知识库读不到不该拖垮 AI 服务启动
            log.warn("加载平台规则知识库失败，AI 问答将不带规则作答（可能表还没建）", e);
            rules = List.of();
        }
        if (CollUtil.isEmpty(rules)) {
            text = "";
            ruleIds = List.of();
            log.warn("平台规则知识库为空，AI 问答将不带规则作答");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (AiRule rule : rules) {
            sb.append("【").append(rule.getTitle()).append("】\n")
                    .append(rule.getContent())
                    .append("\n\n");
        }
        // 先拼正文再一次性发布两个 volatile：避免读到一个「有 id 没正文」的中间态
        text = sb.toString().stripTrailing();
        ruleIds = rules.stream().map(AiRule::getId).toList();
        log.info("平台规则手册已载入：{} 条规则，{} 字", rules.size(), text.length());
    }

    /** 全部规则拼成的文本，注入系统提示词的 {@code {{rules}}} 占位符。未载入成功时返回空串。 */
    public String text() {
        return text;
    }

    /** 已载入的规则 id（顺序同 id 升序）。 */
    public List<Long> ruleIds() {
        return ruleIds;
    }
}
