package com.linggong.ai.eval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 一个检索方案在整份评测集上的成绩单：总指标 + 分 category / 分 tag / 分精查子集 + 超纲拒答。
 *
 * <p><b>为什么主指标只算「有答案的题」</b>：超纲题（{@code relevant} 为空）的 Recall / NDCG 在数学上无定义
 * （分母为 0），硬塞进平均值只能靠拍一个「拒答得几分」的主观权重，会污染主指标的可比性。
 * 因此超纲题<b>单独</b>用「拒答准确率」评价，两组指标一起看才完整。
 *
 * <p><b>为什么平均返回条数也要报</b>：{@code Precision@k} 的分母恒为 k，如果一个方案靠「少返回」
 * 把准确率做得很好看，看这一列就能立刻识破 —— 它是准确率的重影。
 */
public final class EvalReport {

    /** 统一跑全档 k，生产口径另由 {@link EvalSet#mainK()} 指出。 */
    public static final List<Integer> K_VALUES = List.of(1, 3, 5, 10);

    /** 无标签样本的切片名，与真实 tag 区分开 */
    public static final String NO_TAG = "（无标签）";

    private static final String NA = "—";

    private final EvalSet set;
    private final String scheme;
    private final List<QueryOutcome> outcomes;

    private EvalReport(EvalSet set, String scheme, List<QueryOutcome> outcomes) {
        this.set = set;
        this.scheme = scheme;
        this.outcomes = List.copyOf(outcomes);
    }

    /**
     * 跑一遍评测。检索器输出不合契约（返回 null / 含重复 id / 含语料中不存在的 id）时直接抛错。
     *
     * <p>刻意选择「抛错」而非「扣分」：幻觉 id 和重复返回都是实现缺陷，把它们摊平成
     * 「分数低一点」会让真正的 bug 藏在一堆看起来合理的指标里。
     */
    public static EvalReport evaluate(EvalSet set, String scheme, RankingRetriever retriever) {
        List<QueryOutcome> outcomes = new ArrayList<>(set.samples().size());
        for (EvalSample s : set.samples()) {
            List<Long> retrieved = retriever.retrieve(s.query());
            if (retrieved == null) {
                throw new IllegalStateException("检索器 [" + scheme + "] 返回了 null，契约要求返回空列表。query="
                        + s.id() + " / " + s.query());
            }
            outcomes.add(new QueryOutcome(s, s.relevantSet(), retrieved));
        }
        EvalReport report = new EvalReport(set, scheme, outcomes);
        List<String> problems = report.consistencyProblems();
        if (!problems.isEmpty()) {
            String detail = String.join("；", problems.subList(0, Math.min(10, problems.size())));
            throw new IllegalStateException("检索器 [" + scheme + "] 输出不合契约，共 " + problems.size()
                    + " 处：" + detail + (problems.size() > 10 ? " …" : ""));
        }
        return report;
    }

    public String scheme() {
        return scheme;
    }

    public EvalSet set() {
        return set;
    }

    public List<QueryOutcome> outcomes() {
        return outcomes;
    }

    public int sampleCount() {
        return outcomes.size();
    }

    // ===== 聚合 =====

    /** 总指标：只含「有答案」的样本。 */
    public Aggregate overall() {
        return aggregate("总计", outcomes);
    }

    /** 按单一主考点切片，顺序与评测集声明一致。 */
    public Map<String, Aggregate> byCategory() {
        Map<String, Aggregate> result = new LinkedHashMap<>();
        for (String category : set.categories()) {
            result.put(category, aggregate(category, filter(o -> category.equals(o.category()))));
        }
        return result;
    }

    /**
     * 按多维难点标签切片。一条样本可带多个 tag，会同时计入多行，<b>行之间不可相加</b>。
     * 末尾额外给一行「无标签」样本，充当「普通难度」的参照。
     */
    public Map<String, Aggregate> byTag() {
        Map<String, Aggregate> result = new LinkedHashMap<>();
        for (String tag : set.tagVocabulary()) {
            result.put(tag, aggregate(tag, filter(o -> o.tags().contains(tag))));
        }
        result.put(NO_TAG, aggregate(NO_TAG, filter(o -> o.tags().isEmpty())));
        return result;
    }

    /** 人工精查子集 vs 其余样本 —— 用来检验「精查过的那批是不是真的更难」。 */
    public Map<String, Aggregate> byHardReview() {
        Map<String, Aggregate> result = new LinkedHashMap<>();
        result.put("人工精查子集", aggregate("人工精查子集", filter(QueryOutcome::hardReview)));
        result.put("其余样本", aggregate("其余样本", filter(o -> !o.hardReview())));
        return result;
    }

    /** 超纲拒答总览。 */
    public Rejection rejection() {
        return Rejection.of("全部超纲题", filter(QueryOutcome::outOfScope));
    }

    /** 超纲拒答按标签切片：near_miss 与「明显无关」的差距，就是「无分数阈值」这个缺陷的暴露程度。 */
    public Map<String, Rejection> rejectionByTag() {
        List<QueryOutcome> oos = filter(QueryOutcome::outOfScope);
        Map<String, Rejection> result = new LinkedHashMap<>();
        for (String tag : set.tagVocabulary()) {
            List<QueryOutcome> slice = oos.stream().filter(o -> o.tags().contains(tag)).toList();
            if (!slice.isEmpty()) {
                result.put(tag, Rejection.of(tag, slice));
            }
        }
        List<QueryOutcome> noTag = oos.stream().filter(o -> o.tags().isEmpty()).toList();
        if (!noTag.isEmpty()) {
            result.put(NO_TAG, Rejection.of(NO_TAG, noTag));
        }
        return result;
    }

    private List<QueryOutcome> filter(java.util.function.Predicate<QueryOutcome> p) {
        return outcomes.stream().filter(p).toList();
    }

    private static Aggregate aggregate(String group, List<QueryOutcome> all) {
        List<QueryOutcome> inScope = all.stream().filter(o -> !o.outOfScope()).toList();
        Map<Integer, Double> hit = new LinkedHashMap<>();
        Map<Integer, Double> recall = new LinkedHashMap<>();
        Map<Integer, Double> precision = new LinkedHashMap<>();
        Map<Integer, Double> mrr = new LinkedHashMap<>();
        Map<Integer, Double> ndcg = new LinkedHashMap<>();
        Map<Integer, Double> returned = new LinkedHashMap<>();
        int n = inScope.size();
        for (int k : K_VALUES) {
            double sh = 0;
            double sr = 0;
            double sp = 0;
            double sm = 0;
            double sn = 0;
            double sret = 0;
            for (QueryOutcome o : inScope) {
                RankingMetrics.ScoreAtK s = RankingMetrics.scoreAtK(o.retrieved(), o.relevant(), k);
                sh += s.hit();
                sr += s.recall();
                sp += s.precision();
                sm += s.reciprocalRank();
                sn += s.ndcg();
                sret += Math.min(k, o.retrieved().size());
            }
            hit.put(k, n == 0 ? 0 : sh / n);
            recall.put(k, n == 0 ? 0 : sr / n);
            precision.put(k, n == 0 ? 0 : sp / n);
            mrr.put(k, n == 0 ? 0 : sm / n);
            ndcg.put(k, n == 0 ? 0 : sn / n);
            returned.put(k, n == 0 ? 0 : sret / n);
        }
        double emptyRate = n == 0 ? 0 : inScope.stream().filter(QueryOutcome::emptyRetrieval).count() / (double) n;
        return new Aggregate(group, n, all.size() - n, hit, recall, precision, mrr, ndcg, returned, emptyRate);
    }

    /**
     * 一个切片上的聚合成绩。
     *
     * @param group              切片名
     * @param n                  参与主指标的样本数（已排除超纲题）
     * @param excludedOutOfScope 被主指标排除的超纲样本数
     * @param emptyRetrievalRate 空召回率：有答案的样本里「一条都没召回」的占比
     */
    public record Aggregate(String group, int n, int excludedOutOfScope,
                            Map<Integer, Double> hit, Map<Integer, Double> recall,
                            Map<Integer, Double> precision, Map<Integer, Double> mrr,
                            Map<Integer, Double> ndcg, Map<Integer, Double> avgRetrieved,
                            double emptyRetrievalRate) {

        public Aggregate {
            hit = Map.copyOf(hit);
            recall = Map.copyOf(recall);
            precision = Map.copyOf(precision);
            mrr = Map.copyOf(mrr);
            ndcg = Map.copyOf(ndcg);
            avgRetrieved = Map.copyOf(avgRetrieved);
        }

        /** 该切片没有可算主指标的样本（例如 near_miss 标签下全是超纲题）。 */
        public boolean noData() {
            return n == 0;
        }

        public double hit(int k) {
            return hit.getOrDefault(k, 0.0);
        }

        public double recall(int k) {
            return recall.getOrDefault(k, 0.0);
        }

        public double precision(int k) {
            return precision.getOrDefault(k, 0.0);
        }

        public double mrr(int k) {
            return mrr.getOrDefault(k, 0.0);
        }

        public double ndcg(int k) {
            return ndcg.getOrDefault(k, 0.0);
        }

        public double avgRetrieved(int k) {
            return avgRetrieved.getOrDefault(k, 0.0);
        }
    }

    /**
     * 超纲题拒答成绩。
     *
     * @param total    该切片的超纲样本数
     * @param rejected 其中「正确地一条都没召回」的条数
     */
    public record Rejection(String slice, int total, int rejected) {

        static Rejection of(String slice, List<QueryOutcome> outOfScope) {
            int rejected = (int) outOfScope.stream().filter(QueryOutcome::emptyRetrieval).count();
            return new Rejection(slice, outOfScope.size(), rejected);
        }

        public double accuracy() {
            return total == 0 ? 0 : (double) rejected / total;
        }

        /** 误召回率 = 1 - 拒答准确率，即「不该回答却回答了」的比例。 */
        public double falseRecallRate() {
            return total == 0 ? 0 : 1 - accuracy();
        }
    }

    // ===== 契约校验 =====

    private List<String> consistencyProblems() {
        List<String> problems = new ArrayList<>();
        Set<Long> legal = set.ruleIds();
        for (QueryOutcome o : outcomes) {
            if (o.retrieved().size() > legal.size()) {
                problems.add(o.id() + " 返回 " + o.retrieved().size() + " 条，超过语料规模 " + legal.size());
            }
            if (new HashSet<>(o.retrieved()).size() != o.retrieved().size()) {
                problems.add(o.id() + " 返回结果含重复 id");
            }
            for (Long id : o.retrieved()) {
                if (!legal.contains(id)) {
                    problems.add(o.id() + " 返回了语料中不存在的规则 id " + id);
                }
            }
        }
        return problems;
    }

    // ===== 渲染 =====

    /** 渲染成 Markdown，可直接贴进评测报告。 */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 检索方案：").append(scheme).append("\n\n");
        sb.append("样本 ").append(sampleCount()).append(" 条｜参与主指标 ")
                .append(overall().n()).append(" 条｜超纲排除 ")
                .append(overall().excludedOutOfScope()).append(" 条\n\n");

        sb.append("### 总指标（k 敏感度）\n\n");
        sb.append("| 指标 |");
        for (int k : K_VALUES) {
            sb.append(' ').append(k == set.mainK() ? "**k=" + k + "（线上）**" : "k=" + k).append(" |");
        }
        sb.append("\n| --- |");
        for (int ignored : K_VALUES) {
            sb.append(" --- |");
        }
        sb.append('\n');
        Aggregate all = overall();
        appendRow(sb, "Hit@k", all, Aggregate::hit);
        appendRow(sb, "Recall@k", all, Aggregate::recall);
        appendRow(sb, "Precision@k", all, Aggregate::precision);
        appendRow(sb, "MRR@k", all, Aggregate::mrr);
        appendRow(sb, "NDCG@k", all, Aggregate::ndcg);
        appendRow(sb, "平均返回条数", all, Aggregate::avgRetrieved);
        sb.append("\n空召回率（有答案却一条没召回）：").append(pct(all.emptyRetrievalRate())).append("\n\n");

        appendGroupTable(sb, "分 category（单一主考点）", byCategory().values());
        appendGroupTable(sb, "分 tag（多标签，行不可相加）", byTag().values());
        appendGroupTable(sb, "精查子集 vs 其余", byHardReview().values());

        sb.append("### 超纲拒答\n\n");
        sb.append("| 切片 | 超纲样本 | 正确拒答 | 拒答准确率 | 误召回率 |\n| --- | --- | --- | --- | --- |\n");
        appendRejection(sb, rejection());
        for (Rejection r : rejectionByTag().values()) {
            appendRejection(sb, r);
        }
        return sb.toString();
    }

    private void appendGroupTable(StringBuilder sb, String title, Iterable<Aggregate> rows) {
        int k = set.mainK();
        sb.append("### ").append(title).append("\n\n");
        sb.append("| 切片 | n | 超纲排除 | Hit@").append(k).append(" | Recall@").append(k)
                .append(" | Precision@").append(k).append(" | MRR@").append(k)
                .append(" | NDCG@").append(k).append(" | 平均返回 | 空召回率 |\n");
        sb.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (Aggregate a : rows) {
            sb.append("| ").append(a.group()).append(" | ").append(a.n()).append(" | ")
                    .append(a.excludedOutOfScope()).append(" | ");
            if (a.noData()) {
                for (int i = 0; i < 6; i++) {
                    sb.append(NA).append(" | ");
                }
                sb.append(NA).append(" |\n");
            } else {
                sb.append(f(a.hit(k))).append(" | ")
                        .append(f(a.recall(k))).append(" | ")
                        .append(f(a.precision(k))).append(" | ")
                        .append(f(a.mrr(k))).append(" | ")
                        .append(f(a.ndcg(k))).append(" | ")
                        .append(f(a.avgRetrieved(k))).append(" | ")
                        .append(pct(a.emptyRetrievalRate())).append(" |\n");
            }
        }
        sb.append('\n');
    }

    private static void appendRow(StringBuilder sb, String label, Aggregate a,
                                  java.util.function.ToDoubleBiFunction<Aggregate, Integer> pick) {
        sb.append("| ").append(label).append(" |");
        for (int k : K_VALUES) {
            sb.append(' ').append(f(pick.applyAsDouble(a, k))).append(" |");
        }
        sb.append('\n');
    }

    private static void appendRejection(StringBuilder sb, Rejection r) {
        sb.append("| ").append(r.slice()).append(" | ").append(r.total()).append(" | ")
                .append(r.rejected()).append(" | ").append(pct(r.accuracy())).append(" | ")
                .append(pct(r.falseRecallRate())).append(" |\n");
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v * 100);
    }
}
