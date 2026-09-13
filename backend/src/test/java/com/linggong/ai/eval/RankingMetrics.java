package com.linggong.ai.eval;

import java.util.List;
import java.util.Set;

/**
 * 检索排序指标的纯函数实现。
 *
 * <p>本类不依赖 Spring / DB / LLM，输入「检索器返回的有序 id 列表 + 标准答案 id 集合 + 截断位 k」，
 * 输出一个 double，因此可以脱离整条链路单独做单元测试 —— 指标算错是评测里最隐蔽也最致命的错误，
 * 一个 off-by-one 就能让「优化有效」的结论完全反过来。
 *
 * <p><b>口径约定</b>（写死在这里，避免不同人算出不同数字）：
 * <ul>
 *   <li>相关性二值：命中记 1、未命中记 0。语料是几十字的短规则文本，没有「部分相关」的标注依据，
 *       强行分层只会引入主观噪声；</li>
 *   <li>{@code Precision@k} 分母恒为 k（TREC 口径）。即使检索器只返回了 m&lt;k 条也按 k 算，
 *       因为「没返回」在业务上等价于「返回了一个不相关的答案」。逐方案同时输出平均返回条数，
 *       便于读者自行判断该口径带来的影响；</li>
 *   <li>{@code MRR@k} 只看<b>第一条</b>命中的位置，因此多答案样本（cross 类，需 2~3 条规则才能答全）
 *       天然吃亏 —— 这正是必须同时输出 {@code Recall@k} 的原因，两者一起看才能区分
 *       「答偏了」和「只答了一半」；</li>
 *   <li>{@code NDCG@k} 折损系数取 {@code 1/log2(i+2)}（第 1 位折损为 1），
 *       IDCG 按 {@code min(k, |R|)} 个理想命中计算；</li>
 *   <li>{@code relevant} 为空（超纲题）时全部指标无定义，统一返回 0 而<b>不是</b> NaN。
 *       调用方必须把超纲题排除在聚合之外，它们只参与「拒答准确率」——
 *       见 {@link EvalReport#rejection()}。</li>
 * </ul>
 */
public final class RankingMetrics {

    private RankingMetrics() {
    }

    /**
     * 一次算齐某个截断位上的五个指标。
     *
     * <p>放在一个 record 里返回而不是拆成五个方法，是因为上层聚合时本来就要对每条样本
     * 把五个指标一起累加，分开算会把同一个排序结果重复扫五遍。
     *
     * @param hit            是否命中（1/0），k 位内有任意一条相关即为 1
     * @param recall         召回率 = 命中数 / |R|
     * @param precision      准确率 = 命中数 / k（分母恒为 k，见类注释）
     * @param reciprocalRank 1 / 第一条命中的位次（1-based），未命中为 0
     * @param ndcg           归一化折损累计增益
     */
    public record ScoreAtK(double hit, double recall, double precision,
                           double reciprocalRank, double ndcg) {

        public static final ScoreAtK ZERO = new ScoreAtK(0, 0, 0, 0, 0);
    }

    /**
     * 计算一条 query 在截断位 k 上的全部指标。
     *
     * @param ranked   检索器返回的规则 id，<b>按相关性降序</b>；允许为空列表（= 不召回）
     * @param relevant 标准答案规则 id 集合；为空表示超纲题，直接返回全 0
     * @param k        截断位，必须 &gt; 0
     */
    public static ScoreAtK scoreAtK(List<Long> ranked, Set<Long> relevant, int k) {
        if (k <= 0 || ranked == null || ranked.isEmpty() || relevant == null || relevant.isEmpty()) {
            return ScoreAtK.ZERO;
        }
        int limit = Math.min(k, ranked.size());
        int hits = 0;
        int firstHitRank = -1;
        double dcg = 0;
        for (int i = 0; i < limit; i++) {
            if (relevant.contains(ranked.get(i))) {
                hits++;
                if (firstHitRank < 0) {
                    firstHitRank = i + 1;
                }
                dcg += discount(i);
            }
        }
        double idcg = 0;
        for (int i = 0; i < Math.min(k, relevant.size()); i++) {
            idcg += discount(i);
        }
        return new ScoreAtK(
                hits > 0 ? 1.0 : 0.0,
                (double) hits / relevant.size(),
                (double) hits / k,
                firstHitRank < 0 ? 0.0 : 1.0 / firstHitRank,
                idcg == 0 ? 0.0 : dcg / idcg);
    }

    // ===== 单指标入口：主要给测试和外部调用用，内部聚合走 scoreAtK 一次算齐 =====

    public static double hitAtK(List<Long> ranked, Set<Long> relevant, int k) {
        return scoreAtK(ranked, relevant, k).hit();
    }

    public static double recallAtK(List<Long> ranked, Set<Long> relevant, int k) {
        return scoreAtK(ranked, relevant, k).recall();
    }

    public static double precisionAtK(List<Long> ranked, Set<Long> relevant, int k) {
        return scoreAtK(ranked, relevant, k).precision();
    }

    public static double reciprocalRankAtK(List<Long> ranked, Set<Long> relevant, int k) {
        return scoreAtK(ranked, relevant, k).reciprocalRank();
    }

    public static double ndcgAtK(List<Long> ranked, Set<Long> relevant, int k) {
        return scoreAtK(ranked, relevant, k).ndcg();
    }

    /**
     * 第 i 位（0-based）的折损系数 {@code 1 / log2(i + 2)}。
     *
     * <p>第 1 位（i=0）折损系数恰为 {@code 1/log2(2) = 1}，第 2 位 {@code 1/log2(3) ≈ 0.6309}。
     * 这里是最容易写成 {@code log2(i+1)} 的地方 —— 那样第 1 位会变成除零、第 2 位折损变成 1，
     * 整个 NDCG 就废了。{@link RankingMetricsTest} 有专门一条用例钉住它。
     */
    private static double discount(int i) {
        return 1.0 / (Math.log(i + 2.0) / Math.log(2.0));
    }
}
