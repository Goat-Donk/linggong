package com.linggong.ai.eval;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 指标引擎的标定：先用三个「已知答案」的检索器验证尺子是准的，再去量真实方案。
 *
 * <p>这里的期望值全部是手算的，不是跑出来抄的。以 oracle 为例，它的
 * {@code Recall@1 = (375×1 + 48×0.5 + 2×(1/3)) / 425 ≈ 0.9404}：
 * 375 条单答案样本首位命中得满分，48 条双答案只答一半，2 条三答案只答三分之一。
 * 只要切分、聚合、折损系数任一环节写错，这个数就对不上。
 */
class EvalReportTest {

    /** 参与主指标的样本数：500 - 75 条超纲 */
    private static final int IN_SCOPE = 425;
    private static final int OUT_OF_SCOPE = 75;

    private static EvalSet set;

    @BeforeAll
    static void loadOnce() {
        set = EvalSet.load();
    }

    @Test
    @DisplayName("oracle（直接返回标准答案）= 指标上限，且与手算值一致")
    void oracleHitsTheCeiling() {
        EvalReport r = EvalReport.evaluate(set, "oracle-上限参照", SanityRetrievers.oracle(set));
        EvalReport.Aggregate a = r.overall();

        assertThat(a.n()).isEqualTo(IN_SCOPE);
        assertThat(a.excludedOutOfScope()).isEqualTo(OUT_OF_SCOPE);

        // |R| ≤ 3 = 线上 top-k，所以 recall/ndcg 在 k=3 处必然触顶
        assertThat(a.hit(1)).isEqualTo(1.0);
        assertThat(a.recall(3)).isCloseTo(1.0, within(1e-12));
        assertThat(a.ndcg(3)).isCloseTo(1.0, within(1e-12));
        assertThat(a.mrr(3)).isEqualTo(1.0);

        // 手算：375×1 + 48×0.5 + 2×(1/3) = 399.6667，再除以 425
        assertThat(a.recall(1)).isCloseTo(399.6666666666667 / IN_SCOPE, within(1e-9));
        // 手算：命中数 375×1 + 48×2 + 2×3 = 477
        assertThat(a.precision(3)).isCloseTo(477.0 / (IN_SCOPE * 3), within(1e-12));
        assertThat(a.avgRetrieved(3)).isCloseTo(477.0 / IN_SCOPE, within(1e-12));

        assertThat(a.emptyRetrievalRate()).isZero();
        // 超纲题的答案是空集，oracle 原样返回空 → 必然正确拒答
        assertThat(r.rejection().accuracy()).isEqualTo(1.0);

        System.out.println(r.toMarkdown());
    }

    @Test
    @DisplayName("空召回策略：主指标全 0，拒答准确率却是 100% —— 单看一组指标都能被平凡策略刷满")
    void alwaysEmptyMaxesRejectionButZeroesEverythingElse() {
        EvalReport r = EvalReport.evaluate(set, "always-empty-下限参照", SanityRetrievers.alwaysEmpty());
        EvalReport.Aggregate a = r.overall();

        for (int k : EvalReport.K_VALUES) {
            assertThat(a.hit(k)).isZero();
            assertThat(a.recall(k)).isZero();
            assertThat(a.precision(k)).isZero();
            assertThat(a.mrr(k)).isZero();
            assertThat(a.ndcg(k)).isZero();
            assertThat(a.avgRetrieved(k)).isZero();
        }
        assertThat(a.emptyRetrievalRate()).isEqualTo(1.0);
        // 这就是必须同时保留「主指标 + 拒答准确率」两组口径的原因
        assertThat(r.rejection().accuracy()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("随机基线：既不可能是 0，也不可能逼近上限，且同种子完全可复现")
    void randomIsBracketedAndDeterministic() {
        EvalReport first = EvalReport.evaluate(set, "random-随机基线", SanityRetrievers.random(set, 20260913L));
        EvalReport again = EvalReport.evaluate(set, "random-随机基线", SanityRetrievers.random(set, 20260913L));
        EvalReport.Aggregate a = first.overall();

        assertThat(again.overall().hit(3)).isEqualTo(a.hit(3));
        assertThat(again.overall().ndcg(3)).isEqualTo(a.ndcg(3));

        // 19 条语料里瞎猜：Hit@1 期望约 1/19，Hit@3 约 3/19
        assertThat(a.hit(1)).isGreaterThan(0.0).isLessThan(0.20);
        assertThat(a.hit(3)).isGreaterThan(a.hit(1));
        assertThat(a.hit(3)).isLessThan(1.0);

        // 每次都返回全部 19 条 → 永远不会「空召回」，拒答准确率必然为 0
        assertThat(a.emptyRetrievalRate()).isZero();
        assertThat(first.rejection().accuracy()).isZero();

        System.out.printf("random-随机基线：Hit@1=%.4f Hit@3=%.4f MRR@3=%.4f NDCG@3=%.4f%n",
                a.hit(1), a.hit(3), a.mrr(3), a.ndcg(3));
    }

    @Test
    @DisplayName("三个参照检索器形成有序区间：oracle ≥ random ≥ always-empty")
    void sanityRetrieversAreOrdered() {
        double oracle = EvalReport.evaluate(set, "oracle", SanityRetrievers.oracle(set)).overall().hit(3);
        double random = EvalReport.evaluate(set, "random", SanityRetrievers.random(set, 20260913L)).overall().hit(3);
        double empty = EvalReport.evaluate(set, "empty", SanityRetrievers.alwaysEmpty()).overall().hit(3);

        assertThat(oracle).isGreaterThan(random);
        assertThat(random).isGreaterThan(empty);
    }

    @Test
    @DisplayName("cross 是唯一需要多答案的类别，Recall@1 天然只有一半左右")
    void crossCategoryNeedsMultiAnswerRecall() {
        EvalReport r = EvalReport.evaluate(set, "oracle", SanityRetrievers.oracle(set));
        EvalReport.Aggregate cross = r.byCategory().get("cross");

        assertThat(cross.n()).isEqualTo(50);
        // 48 条 |R|=2 + 2 条 |R|=3，首位只能命中一条
        assertThat(cross.recall(1)).isCloseTo((48 * 0.5 + 2 * (1.0 / 3)) / 50, within(1e-9));
        assertThat(cross.recall(3)).isCloseTo(1.0, within(1e-12));
    }

    @Test
    @DisplayName("切片口径：near_miss 全是超纲题，主指标无数据；无标签切片 206 条")
    void slicesExcludeOutOfScopeCorrectly() {
        EvalReport r = EvalReport.evaluate(set, "oracle", SanityRetrievers.oracle(set));

        EvalReport.Aggregate nearMiss = r.byTag().get("near_miss");
        assertThat(nearMiss.noData()).isTrue();
        assertThat(nearMiss.excludedOutOfScope()).isEqualTo(40);
        assertThat(nearMiss.group()).isEqualTo("near_miss");

        EvalReport.Aggregate noTag = r.byTag().get(EvalReport.NO_TAG);
        assertThat(noTag.n()).isEqualTo(206);
        assertThat(noTag.excludedOutOfScope()).isEqualTo(35);

        // 精查子集 120 条全部有答案（negation / cross / conflict 都不属于超纲）
        assertThat(r.byHardReview().get("人工精查子集").n()).isEqualTo(120);
        assertThat(r.byCategory()).containsOnlyKeys(
                "direct", "colloquial", "typo", "negation", "cross", "out_of_scope");
    }

    @Test
    @DisplayName("超纲拒答按标签切片：near_miss 与明显无关分开统计")
    void rejectionSlicesByTag() {
        EvalReport r = EvalReport.evaluate(set, "always-empty", SanityRetrievers.alwaysEmpty());
        assertThat(r.rejection().total()).isEqualTo(OUT_OF_SCOPE);
        assertThat(r.rejectionByTag()).containsKeys("near_miss", EvalReport.NO_TAG);
        assertThat(r.rejectionByTag().get("near_miss").total()).isEqualTo(40);
        assertThat(r.rejectionByTag().get(EvalReport.NO_TAG).total()).isEqualTo(35);
    }

    @Test
    @DisplayName("全表不得出现 NaN / Infinity（一个 NaN 能悄悄吃掉整列平均值）")
    void noSliceProducesNaN() {
        EvalReport r = EvalReport.evaluate(set, "random", SanityRetrievers.random(set, 1L));
        List<EvalReport.Aggregate> all = new java.util.ArrayList<>();
        all.add(r.overall());
        all.addAll(r.byCategory().values());
        all.addAll(r.byTag().values());
        all.addAll(r.byHardReview().values());
        for (EvalReport.Aggregate a : all) {
            for (int k : EvalReport.K_VALUES) {
                assertThat(Double.isFinite(a.hit(k))).as("%s Hit@%d", a.group(), k).isTrue();
                assertThat(Double.isFinite(a.recall(k))).as("%s Recall@%d", a.group(), k).isTrue();
                assertThat(Double.isFinite(a.precision(k))).as("%s P@%d", a.group(), k).isTrue();
                assertThat(Double.isFinite(a.mrr(k))).as("%s MRR@%d", a.group(), k).isTrue();
                assertThat(Double.isFinite(a.ndcg(k))).as("%s NDCG@%d", a.group(), k).isTrue();
            }
            assertThat(Double.isFinite(a.emptyRetrievalRate())).as("%s 空召回率", a.group()).isTrue();
        }
        assertThat(r.toMarkdown()).doesNotContain("NaN").doesNotContain("Infinity");
    }

    @Test
    @DisplayName("Markdown 渲染包含主指标口径标记，且无数据切片渲染为 —")
    void markdownRendersMainKAndEmptySlices() {
        String md = EvalReport.evaluate(set, "oracle", SanityRetrievers.oracle(set)).toMarkdown();
        assertThat(md).contains("**k=3（线上）**");
        assertThat(md).contains("### 超纲拒答");
        assertThat(md).contains("near_miss");
        assertThat(md).contains("—");
    }

    // ===== 契约校验：检索器实现有缺陷时必须立刻炸，而不是摊平成「分数低一点」 =====

    @Test
    @DisplayName("检索器返回 null → 立刻抛错并指出是哪条 query")
    void nullReturnIsRejected() {
        assertThatThrownBy(() -> EvalReport.evaluate(set, "bad", q -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("返回了 null")
                .hasMessageContaining("q001");
    }

    @Test
    @DisplayName("检索器返回幻觉 id → 立刻抛错，不静默计为未命中")
    void hallucinatedIdIsRejected() {
        assertThatThrownBy(() -> EvalReport.evaluate(set, "bad", q -> List.of(999L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不存在的规则 id 999");
    }

    @Test
    @DisplayName("检索器返回重复 id → 立刻抛错，否则 NDCG 会被重复计分抬高")
    void duplicateIdIsRejected() {
        assertThatThrownBy(() -> EvalReport.evaluate(set, "bad", q -> List.of(20L, 20L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复 id");
    }
}
