package com.linggong.ai.rule.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.linggong.ai.rule.RuleContentRetriever;
import com.linggong.entity.AiRule;
import com.linggong.mapper.AiRuleMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 BM25 关键词检索的平台规则检索器。
 *
 * <p>为什么不直接上向量库：BM25 零额外依赖、结果确定可演示，且规则问答本质是「关键词命中的
 * 短文本召回」，对中文来说 2-gram 词袋 + BM25 已经够用；二期可无痛换成向量（见 {@link RuleContentRetriever}）。
 *
 * <p>实现：
 * <ul>
 *   <li>分词：中文按 2-gram（相邻两字），连续 ASCII 字母/数字按整词（小写），可命中「服务费」「信用分」这类词；</li>
 *   <li>打分：标准 BM25，k1=1.5、b=0.75；IDF 防止「工资」这种高频词压倒性命中；</li>
 *   <li>启动时 {@link #loadRules()} 全表载入内存建索引（规则库小，几十条），查询走内存零 DB 开销。</li>
 * </ul>
 */
@Slf4j
@Component("ruleBm25Retriever")
public class Bm25ContentRetriever implements RuleContentRetriever {

    /** BM25 词频饱和参数 */
    private static final double K1 = 1.5;
    /** BM25 长度归一参数 */
    private static final double B = 0.75;
    /** 连串 CJK 字符（用来切中文 2-gram） */
    private static final Pattern CJK_RUN = Pattern.compile("[\\u4e00-\\u9fa5]+");
    /** 连续 ASCII 字母/数字（当成整词） */
    private static final Pattern ASCII_WORD = Pattern.compile("[a-zA-Z0-9]+");

    private final AiRuleMapper aiRuleMapper;
    private final int topK;

    /** 索引：规则 id → 内容（含 title/tags/content 拼成的检索文本） */
    private final List<IndexedRule> index = new ArrayList<>();
    /** id → 文档总词数（doc length） */
    private final Map<Long, Integer> docLength = new HashMap<>();
    /** 词 → 出现在多少篇文档（df） */
    private final Map<String, Integer> docFrequency = new HashMap<>();
    private double avgDocLength = 1;
    private boolean loaded;

    public Bm25ContentRetriever(AiRuleMapper aiRuleMapper,
                                @Value("${linggong.ai.retrieval-top-k:3}") int topK) {
        this.aiRuleMapper = aiRuleMapper;
        this.topK = topK;
    }

    @PostConstruct
    public void loadRules() {
        List<AiRule> rules;
        try {
            rules = aiRuleMapper.selectList(Wrappers.<AiRule>lambdaQuery().orderByAsc(AiRule::getId));
        } catch (Exception e) {
            log.warn("加载平台规则知识库失败，AI 问答的 RAG 检索将空转（可能表还没建）", e);
            rules = List.of();
        }
        synchronized (index) {
            index.clear();
            docLength.clear();
            docFrequency.clear();
            if (CollUtil.isEmpty(rules)) {
                avgDocLength = 1;
                loaded = true;
                return;
            }
            for (AiRule rule : rules) {
                IndexedRule ir = new IndexedRule(rule);
                List<String> tokens = tokenize(ir.searchText);
                ir.tokens = tokens;
                index.add(ir);
                int len = tokens.size();
                docLength.put(rule.getId(), len);
                for (String t : new java.util.HashSet<>(tokens)) {
                    docFrequency.merge(t, 1, Integer::sum);
                }
            }
            avgDocLength = docLength.values().stream().mapToInt(Integer::intValue).average().orElse(1);
            loaded = true;
        }
        log.info("AI 规则知识库已载入：{} 条规则，检索 topK={}", rules.size(), topK);
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (!loaded || query == null || query.text() == null || query.text().isBlank()) {
            return List.of();
        }
        List<String> queryTokens = tokenize(query.text());
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        // 逐篇算 BM25 总分，排序取 top-k
        double[] scores = new double[index.size()];
        for (int i = 0; i < index.size(); i++) {
            IndexedRule ir = index.get(i);
            double total = 0;
            for (String qt : new java.util.HashSet<>(queryTokens)) {
                total += bm25(ir, qt);
            }
            scores[i] = total;
        }
        // 简单选择排序取 top-k（库小，够用），并列按 id 保序
        int n = index.size();
        List<Content> result = new ArrayList<>();
        boolean[] taken = new boolean[n];
        for (int k = 0; k < Math.min(topK, n); k++) {
            int best = -1;
            for (int i = 0; i < n; i++) {
                if (taken[i]) {
                    continue;
                }
                if (best < 0 || scores[i] > scores[best]) {
                    best = i;
                }
            }
            if (best < 0 || scores[best] <= 0) {
                break; // 后面都是 0 分，无命中
            }
            taken[best] = true;
            IndexedRule ir = index.get(best);
            TextSegment segment = TextSegment.from(
                    "【" + ir.rule.getTitle() + "】\n" + ir.rule.getContent(),
                    dev.langchain4j.data.document.Metadata.from(
                            Map.of("ruleId", String.valueOf(ir.rule.getId()), "title", ir.rule.getTitle())));
            result.add(Content.from(segment));
        }
        return result;
    }

    /** 单文档单词的 BM25 得分。 */
    private double bm25(IndexedRule ir, String token) {
        int tf = 0;
        for (String t : ir.tokens) {
            if (t.equals(token)) {
                tf++;
            }
        }
        if (tf == 0) {
            return 0;
        }
        int df = docFrequency.getOrDefault(token, 1);
        double n = Math.max(1, index.size());
        double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
        double dl = docLength.getOrDefault(ir.rule.getId(), 0);
        double norm = K1 * (1 - B + B * dl / avgDocLength);
        return idf * (tf * (K1 + 1)) / (tf + norm);
    }

    /**
     * 分词：中文连续段按 2-gram（相邻两字，单字段退化为单字），ASCII 字母/数字按整词小写。
     * 例：「服务费怎么算」→ [服务, 务费, 费怎, 怎么, 么算]；「GEO附近」→ [ge, ... 中文字段…]。
     */
    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        Matcher cjk = CJK_RUN.matcher(text);
        int lastAsciiEnd = 0;
        while (cjk.find()) {
            // 两段中文之间可能有 ASCII 词，先收掉
            collectAscii(text.substring(lastAsciiEnd, cjk.start()), tokens);
            String run = cjk.group();
            if (run.length() == 1) {
                tokens.add(run);
            } else {
                for (int i = 0; i < run.length() - 1; i++) {
                    tokens.add(run.substring(i, i + 2));
                }
            }
            lastAsciiEnd = cjk.end();
        }
        collectAscii(text.substring(lastAsciiEnd), tokens);
        return tokens;
    }

    private static void collectAscii(String part, List<String> tokens) {
        Matcher m = ASCII_WORD.matcher(part);
        while (m.find()) {
            tokens.add(m.group().toLowerCase());
        }
    }

    /** 内存中的规则 + 其检索文本与词袋。 */
    private static final class IndexedRule {
        final AiRule rule;
        final String searchText;
        List<String> tokens = List.of();

        IndexedRule(AiRule rule) {
            this.rule = rule;
            this.searchText = rule.getTitle() + " " + rule.getTags() + " " + rule.getContent();
        }
    }
}
