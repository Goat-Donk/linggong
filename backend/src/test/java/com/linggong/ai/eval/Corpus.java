package com.linggong.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linggong.entity.AiRule;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测语料：{@code tb_ai_rule} 的 19 条规则快照（{@code /ai/rules.json}）。
 *
 * <p>做成快照而不是每次查库，是为了让基线<b>不连 DB 也能复现</b>。否则任何人改一条规则，
 * 历史基线数字就再也算不出来了，评测报告会退化成「当时大概是这个数」的不可信文档。
 * 快照带指纹，报告里声明本次基线对应的语料指纹，漂移可查。
 *
 * <p>三个基线方案（B2/B3/B4）跑的是<b>同一个生产打分器</b>，区别只在喂进去的语料：
 * <ul>
 *   <li>B2 = 原始语料；</li>
 *   <li>B3 = {@link #amplifiedTags} 把 tags 重复若干次；</li>
 *   <li>B4 = 在 B3 基础上再 {@link #withAliases} 补入口语/错别字别名。</li>
 * </ul>
 * 这样 B2→B3→B4 每一步隔离出的恰好是一个变量，而不是「换了套实现」。
 */
public final class Corpus {

    public static final String DEFAULT_RESOURCE = "/ai/rules.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<AiRule> rules;
    private final String fingerprint;

    private Corpus(List<AiRule> rules, String fingerprint) {
        this.rules = List.copyOf(rules);
        this.fingerprint = fingerprint;
    }

    public static Corpus load() {
        return load(DEFAULT_RESOURCE);
    }

    public static Corpus load(String resource) {
        try (InputStream in = Corpus.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("语料快照不在 classpath 上：" + resource
                        + "；应由 tools/snapshot_rules.py 生成到 backend/src/test/resources/ai/");
            }
            JsonNode root = MAPPER.readTree(in);
            List<AiRule> rules = new ArrayList<>();
            for (JsonNode n : root.path("rules")) {
                AiRule r = new AiRule();
                r.setId(n.path("id").asLong());
                r.setTitle(n.path("title").asText());
                r.setTags(n.path("tags").asText());
                r.setContent(n.path("content").asText());
                rules.add(r);
            }
            if (rules.isEmpty()) {
                throw new IllegalStateException("语料快照为空：" + resource);
            }
            return new Corpus(rules, root.path("meta").path("fingerprint").asText(""));
        } catch (IOException e) {
            throw new UncheckedIOException("读取语料快照失败：" + resource, e);
        }
    }

    public List<AiRule> rules() {
        return rules;
    }

    /** 语料指纹，写进评测报告，用于判断报告与语料是否还对得上。 */
    public String fingerprint() {
        return fingerprint;
    }

    /**
     * 检索文本，与生产 {@code Bm25ContentRetriever.IndexedRule} 逐字一致。
     *
     * <p>tags 参与检索但不进入给 LLM 的正文（{@code Content} 只有 title + content），
     * 所以往 tags 里补别名既能改善召回、又不会污染回答上下文 —— 这是别名该放 tags 而不是
     * 塞进 content 的根本原因。
     */
    public static String searchText(AiRule r) {
        return r.getTitle() + " " + r.getTags() + " " + r.getContent();
    }

    /**
     * B3：把 tags 重复 {@code times} 次，抬高 tags 里词项的 tf。
     *
     * <p>这是词袋索引里做<b>字段加权</b>的标准近似手法（严格的字段加权是 BM25F，那需要改打分器）。
     * 用它而不是改 BM25F 的原因是：这样能直接跑生产打分器，B2→B3 的差值就纯粹来自语料。
     *
     * <p>代价要讲清楚：重复同时抬高 docLength，会被 BM25 的 b 归一化<b>部分抵消</b>，
     * 实际加权倍数小于 {@code times}。这个抵消效应本身也是观测对象之一。
     */
    public static List<AiRule> amplifiedTags(List<AiRule> in, int times) {
        if (times < 1) {
            throw new IllegalArgumentException("times 必须 >= 1，当前 " + times);
        }
        List<AiRule> out = new ArrayList<>(in.size());
        for (AiRule r : in) {
            StringBuilder tags = new StringBuilder();
            for (int i = 0; i < times; i++) {
                if (i > 0) {
                    tags.append(',');
                }
                tags.append(r.getTags());
            }
            out.add(copyWithTags(r, tags.toString()));
        }
        return out;
    }

    /**
     * B4：把别名补进 tags。
     *
     * <p>别名只补「语料里还没有」的词（生成器已过滤），所以每个别名都真实降低了 OOV。
     */
    public static List<AiRule> withAliases(List<AiRule> in, Map<Long, List<String>> aliases) {
        List<AiRule> out = new ArrayList<>(in.size());
        for (AiRule r : in) {
            List<String> extra = aliases.getOrDefault(r.getId(), List.of());
            if (extra.isEmpty()) {
                out.add(r);
                continue;
            }
            out.add(copyWithTags(r, r.getTags() + "," + String.join(",", extra)));
        }
        return out;
    }

    /** 统计全部规则 tags 里挂了多少别名，用于报告里说明 B4 的改动量。 */
    public static int aliasCount(Map<Long, List<String>> aliases) {
        return aliases.values().stream().mapToInt(List::size).sum();
    }

    private static AiRule copyWithTags(AiRule src, String tags) {
        AiRule r = new AiRule();
        r.setId(src.getId());
        r.setTitle(src.getTitle());
        r.setTags(tags);
        r.setContent(src.getContent());
        r.setCreateTime(src.getCreateTime());
        return r;
    }

    /** id → 规则，便于按 id 查正文。 */
    public Map<Long, AiRule> byId() {
        Map<Long, AiRule> m = new LinkedHashMap<>();
        for (AiRule r : rules) {
            m.put(r.getId(), r);
        }
        return m;
    }
}
