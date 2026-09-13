package com.linggong.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 评测集：从 classpath 上的 {@code /ai/eval-set.json} 载入的 500 条标注样本 + 规则索引。
 *
 * <p>该文件由 {@code tools/gen_eval_set.py} 生成，<b>不要手工编辑</b>。这里刻意手写 JsonNode 解析
 * 而不是用 Jackson 注解绑定 record，原因是：评测集是「评测的地基」，解析阶段就该对结构问题
 * 报出可读的错误（缺字段、未知 category、答案 id 越界），而不是静默反序列化成一个缺胳膊少腿的对象，
 * 等到算出好看的指标之后才发现地基是歪的。
 *
 * <p>{@link #validate()} 把生成器里那套 Python 校验在 Java 侧又钉了一遍 —— 双保险的意义在于
 * 拦截「绕过生成器直接改 JSON」这条路径。
 */
public record EvalSet(String version,
                      String status,
                      int mainK,
                      int corpusSize,
                      Map<Long, String> rulesIndex,
                      List<EvalSample> samples,
                      Map<String, Integer> declaredQuota) {

    /** 与 {@code gen_eval_set.py} 的 OUT_PATH 对应 */
    public static final String DEFAULT_RESOURCE = "/ai/eval-set.json";

    /** 超纲题的主考点名，与生成器保持一致 */
    public static final String CATEGORY_OUT_OF_SCOPE = "out_of_scope";

    /** 进入人工精查子集的两个类别（外加带 conflict 标签的样本） */
    private static final Set<String> HARD_REVIEW_CATEGORIES = Set.of("negation", "cross");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public EvalSet {
        rulesIndex = Map.copyOf(rulesIndex);
        samples = List.copyOf(samples);
        declaredQuota = Map.copyOf(declaredQuota);
    }

    public static EvalSet load() {
        return load(DEFAULT_RESOURCE);
    }

    public static EvalSet load(String resource) {
        try (InputStream in = EvalSet.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("评测集不在 classpath 上：" + resource
                        + "；应由 tools/gen_eval_set.py 生成到 backend/src/test/resources/ai/");
            }
            JsonNode root = MAPPER.readTree(in);
            JsonNode meta = require(root, "meta");
            JsonNode rulesNode = require(root, "rulesIndex");

            Map<Long, String> rules = new LinkedHashMap<>();
            rulesNode.fieldNames().forEachRemaining(f -> rules.put(Long.valueOf(f), rulesNode.get(f).asText()));

            List<EvalSample> samples = new ArrayList<>();
            for (JsonNode n : require(root, "samples")) {
                List<Long> relevant = new ArrayList<>();
                n.path("relevant").forEach(r -> relevant.add(r.asLong()));
                List<String> tags = new ArrayList<>();
                n.path("tags").forEach(t -> tags.add(t.asText()));
                samples.add(new EvalSample(
                        n.path("id").asText(),
                        n.path("query").asText(),
                        relevant,
                        n.path("category").asText(),
                        tags,
                        n.path("hardReview").asBoolean(false),
                        n.path("note").asText("")));
            }

            Map<String, Integer> quota = new LinkedHashMap<>();
            JsonNode quotaNode = meta.path("quota500");
            quotaNode.fieldNames().forEachRemaining(f -> {
                if (!f.startsWith("_")) {
                    quota.put(f, quotaNode.get(f).asInt());
                }
            });

            return new EvalSet(
                    meta.path("version").asText(""),
                    meta.path("status").asText(""),
                    meta.path("mainK").asInt(3),
                    meta.path("corpusSize").asInt(0),
                    rules, samples, quota);
        } catch (IOException e) {
            throw new UncheckedIOException("读取评测集失败：" + resource, e);
        }
    }

    /** 只参与主指标的样本（排除超纲题）。 */
    public List<EvalSample> inScopeSamples() {
        return samples.stream().filter(s -> !s.outOfScope()).toList();
    }

    /** 超纲样本，只参与「拒答准确率」。 */
    public List<EvalSample> outOfScopeSamples() {
        return samples.stream().filter(EvalSample::outOfScope).toList();
    }

    /** 全部规则 id，即语料的合法边界。 */
    public Set<Long> ruleIds() {
        return rulesIndex.keySet();
    }

    /** 按样本出现顺序去重的 category 列表（用于报告里稳定排序，不依赖字母序）。 */
    public List<String> categories() {
        return samples.stream().map(EvalSample::category).distinct().toList();
    }

    /**
     * 取子集，用于留出验证（如只看 id 奇数的验证集 B）。
     *
     * <p>子集的 {@code declaredQuota} 为空，因此<b>不适用</b> {@link #validate()} 的配额校验
     * （配额天然只对全集成立）。其余校验逻辑不受影响。
     */
    public EvalSet subset(java.util.function.Predicate<EvalSample> predicate) {
        List<EvalSample> picked = samples.stream().filter(predicate).toList();
        if (picked.isEmpty()) {
            throw new IllegalArgumentException("子集为空，切分条件写错了");
        }
        return new EvalSet(version, status, mainK, corpusSize, rulesIndex, picked, Map.of());
    }

    /** 实际出现过的 tag，字母序。 */
    public List<String> tagVocabulary() {
        return samples.stream().flatMap(s -> s.tags().stream()).distinct().sorted().toList();
    }

    /**
     * 结构性校验：只查「自洽性」，不判语义对错（语义靠人工精查那 120 条）。
     *
     * <p>返回空列表表示通过；否则每个元素是一条可读的错误描述。
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (samples.isEmpty()) {
            errors.add("评测集为空");
            return errors;
        }
        if (rulesIndex.isEmpty()) {
            errors.add("rulesIndex 为空，无法校验答案 id 是否越界");
        }
        if (corpusSize != rulesIndex.size()) {
            errors.add("meta.corpusSize=" + corpusSize + " 与 rulesIndex 实际条数 " + rulesIndex.size() + " 不一致");
        }

        Set<String> seenIds = new HashSet<>();
        Set<String> seenQueries = new HashSet<>();
        Map<String, Integer> actualQuota = new TreeMap<>();
        for (EvalSample s : samples) {
            if (!seenIds.add(s.id())) {
                errors.add("id 重复：" + s.id());
            }
            if (!seenQueries.add(s.query())) {
                errors.add("query 重复：" + s.id() + " / " + s.query());
            }
            if (s.category() == null || s.category().isBlank()) {
                errors.add(s.id() + " 缺少 category");
            }
            actualQuota.merge(s.category(), 1, Integer::sum);

            if (new HashSet<>(s.relevant()).size() != s.relevant().size()) {
                errors.add(s.id() + " 的 relevant 含重复 id");
            }
            for (Long id : s.relevant()) {
                if (!rulesIndex.containsKey(id)) {
                    errors.add(s.id() + " 的答案 id " + id + " 不在 rulesIndex 内");
                }
            }

            // 超纲 ⟺ 答案为空，两个方向都要卡
            boolean oos = CATEGORY_OUT_OF_SCOPE.equals(s.category());
            if (oos && !s.relevant().isEmpty()) {
                errors.add(s.id() + " 是超纲题但 relevant 非空");
            }
            if (!oos && s.relevant().isEmpty()) {
                errors.add(s.id() + " 非超纲题但 relevant 为空");
            }

            boolean expectHard = HARD_REVIEW_CATEGORIES.contains(s.category()) || s.tagged("conflict");
            if (expectHard != s.hardReview()) {
                errors.add(s.id() + " 的 hardReview=" + s.hardReview() + "，按不变式应为 " + expectHard);
            }

            if (new HashSet<>(s.tags()).size() != s.tags().size()) {
                errors.add(s.id() + " 的 tags 含重复项");
            }
            if (s.tags().stream().anyMatch(t -> t == null || t.isBlank())) {
                errors.add(s.id() + " 的 tags 含空项");
            }
        }

        for (Map.Entry<String, Integer> e : declaredQuota.entrySet()) {
            int actual = actualQuota.getOrDefault(e.getKey(), 0);
            if (actual != e.getValue()) {
                errors.add("类别 " + e.getKey() + " 实际 " + actual + " 条，meta 声明 " + e.getValue() + " 条");
            }
        }
        for (Map.Entry<String, Integer> e : actualQuota.entrySet()) {
            if (!declaredQuota.containsKey(e.getKey())) {
                errors.add("类别 " + e.getKey() + " 有 " + e.getValue() + " 条，但 meta.quota500 未声明该类别");
            }
        }
        return errors;
    }

    private static JsonNode require(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            throw new IllegalStateException("评测集缺少必填字段：" + field);
        }
        return node;
    }
}
