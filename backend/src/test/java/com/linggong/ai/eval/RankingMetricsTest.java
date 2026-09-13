package com.linggong.ai.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 指标口径的手工用例。
 *
 * <p>这里的每一条断言都是拿纸笔算出来的，而不是「跑一遍看输出多少就写多少」——
 * 后者只能证明代码没变，不能证明代码是对的。尤其是 NDCG 的 off-by-one：
 * 写成 {@code log2(i+1)} 之后指标依然「看起来正常」，只有手算值能钉住它。
 */
class RankingMetricsTest {

    private static final long A = 20L;
    private static final long B = 31L;
    private static final long C = 33L;
    /** 一个不相关的 id */
    private static final long X = 99L;

    @Test
    @DisplayName("完美命中：第 1 位就是答案，五项全为 1")
    void perfectTopOne() {
        RankingMetrics.ScoreAtK s = RankingMetrics.scoreAtK(List.of(A), Set.of(A), 1);
        assertThat(s.hit()).isEqualTo(1.0);
        assertThat(s.recall()).isEqualTo(1.0);
        assertThat(s.precision()).isEqualTo(1.0);
        assertThat(s.reciprocalRank()).isEqualTo(1.0);
        assertThat(s.ndcg()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("NDCG 折损系数：答案在第 2 位时 NDCG=1/log2(3)≈0.6309，不是 1")
    void ndcgDiscountIsNotOffByOne() {
        RankingMetrics.ScoreAtK s = RankingMetrics.scoreAtK(List.of(X, A), Set.of(A), 2);
        // DCG = 1/log2(3)；IDCG = 1/log2(2) = 1
        assertThat(s.ndcg()).isCloseTo(0.6309297535714574, within(1e-12));
        assertThat(s.hit()).isEqualTo(1.0);
        assertThat(s.recall()).isEqualTo(1.0);
        assertThat(s.precision()).isEqualTo(0.5);
        assertThat(s.reciprocalRank()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("多答案：MRR 只看第一条命中，Recall 才能反映「答全了」")
    void mrrOnlyCountsFirstHit() {
        RankingMetrics.ScoreAtK s = RankingMetrics.scoreAtK(List.of(X, A, B), Set.of(A, B), 3);
        assertThat(s.reciprocalRank()).isEqualTo(0.5);
        assertThat(s.recall()).isEqualTo(1.0);
        assertThat(s.precision()).isCloseTo(2.0 / 3, within(1e-12));
        // 两条答案实际落在第 2、3 位，理想排序该占第 1、2 位，所以 NDCG 注定拿不到 1
        double dcg = 1.0 / log2(3) + 1.0 / log2(4);
        double idcg = 1.0 / log2(2) + 1.0 / log2(3);
        assertThat(s.ndcg()).isCloseTo(dcg / idcg, within(1e-12));
        assertThat(s.ndcg()).isCloseTo(0.6934264036172708, within(1e-12));
    }

    @Test
    @DisplayName("|R| 大于 k：Recall 按 |R| 打折，Precision 分母仍是 k")
    void recallPenalisedWhenGoldExceedsK() {
        RankingMetrics.ScoreAtK s = RankingMetrics.scoreAtK(List.of(A), Set.of(A, B, C), 1);
        assertThat(s.recall()).isCloseTo(1.0 / 3, within(1e-12));
        assertThat(s.precision()).isEqualTo(1.0);
        assertThat(s.hit()).isEqualTo(1.0);
        assertThat(s.ndcg()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Precision@k 分母恒为 k，不随实际返回条数缩水")
    void precisionDenominatorIsAlwaysK() {
        RankingMetrics.ScoreAtK s = RankingMetrics.scoreAtK(List.of(A), Set.of(A), 3);
        assertThat(s.precision()).isCloseTo(1.0 / 3, within(1e-12));
        assertThat(s.recall()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("零召回：有答案却一条不返回，五项全为 0")
    void emptyRetrievalScoresZero() {
        RankingMetrics.ScoreAtK s = RankingMetrics.scoreAtK(List.of(), Set.of(A), 3);
        assertThat(s).isEqualTo(RankingMetrics.ScoreAtK.ZERO);
    }

    @Test
    @DisplayName("超纲题：答案为空时返回 0 而不是 NaN（NaN 会污染整个平均值）")
    void outOfScopeYieldsZeroNotNaN() {
        RankingMetrics.ScoreAtK s = RankingMetrics.scoreAtK(List.of(A, B), Set.of(), 3);
        assertThat(s).isEqualTo(RankingMetrics.ScoreAtK.ZERO);
        assertAllFinite(s);
    }

    @Test
    @DisplayName("非法 k：k<=0 不应抛异常，统一返回 0")
    void nonPositiveKIsZero() {
        assertThat(RankingMetrics.scoreAtK(List.of(A), Set.of(A), 0)).isEqualTo(RankingMetrics.ScoreAtK.ZERO);
        assertThat(RankingMetrics.scoreAtK(List.of(A), Set.of(A), -1)).isEqualTo(RankingMetrics.ScoreAtK.ZERO);
    }

    @Test
    @DisplayName("k 超过返回条数：不越界，且截断到实际长度后指标不再变化")
    void kBeyondResultSizeIsSafe() {
        RankingMetrics.ScoreAtK at2 = RankingMetrics.scoreAtK(List.of(X, A), Set.of(A), 2);
        RankingMetrics.ScoreAtK at10 = RankingMetrics.scoreAtK(List.of(X, A), Set.of(A), 10);
        assertThat(at10.recall()).isEqualTo(at2.recall());
        assertThat(at10.reciprocalRank()).isEqualTo(at2.reciprocalRank());
        assertThat(at10.ndcg()).isEqualTo(at2.ndcg());
        // 但 Precision 分母是 k，所以会随 k 下降 —— 这正是它必须与「平均返回条数」一起看的原因
        assertThat(at10.precision()).isCloseTo(0.1, within(1e-12));
        assertAllFinite(at10);
    }

    @Test
    @DisplayName("答案全部召回：NDCG 达到 1.0，Precision 仍按 k 打折")
    void fullRecallOfMultiAnswer() {
        RankingMetrics.ScoreAtK s = RankingMetrics.scoreAtK(List.of(A, B, C), Set.of(A, B, C), 10);
        assertThat(s.recall()).isEqualTo(1.0);
        assertThat(s.ndcg()).isCloseTo(1.0, within(1e-12));
        assertThat(s.precision()).isCloseTo(0.3, within(1e-12));
        assertThat(s.reciprocalRank()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("单指标入口与合并入口口径一致")
    void singleMetricEntryPointsAgree() {
        List<Long> ranked = List.of(X, A, B);
        Set<Long> gold = Set.of(A, B);
        RankingMetrics.ScoreAtK s = RankingMetrics.scoreAtK(ranked, gold, 3);
        assertThat(RankingMetrics.hitAtK(ranked, gold, 3)).isEqualTo(s.hit());
        assertThat(RankingMetrics.recallAtK(ranked, gold, 3)).isEqualTo(s.recall());
        assertThat(RankingMetrics.precisionAtK(ranked, gold, 3)).isEqualTo(s.precision());
        assertThat(RankingMetrics.reciprocalRankAtK(ranked, gold, 3)).isEqualTo(s.reciprocalRank());
        assertThat(RankingMetrics.ndcgAtK(ranked, gold, 3)).isEqualTo(s.ndcg());
    }

    private static double log2(double v) {
        return Math.log(v) / Math.log(2);
    }

    private static void assertAllFinite(RankingMetrics.ScoreAtK s) {
        assertThat(Double.isFinite(s.hit())).isTrue();
        assertThat(Double.isFinite(s.recall())).isTrue();
        assertThat(Double.isFinite(s.precision())).isTrue();
        assertThat(Double.isFinite(s.reciprocalRank())).isTrue();
        assertThat(Double.isFinite(s.ndcg())).isTrue();
    }
}
