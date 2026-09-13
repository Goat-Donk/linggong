package com.linggong.ai.eval;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对真实 500 条评测集的结构校验。
 *
 * <p>这些断言与 {@code tools/gen_eval_set.py} 里的 Python 校验刻意重复 ——
 * 生成器管住「按脚本重新生成」这条路径，这里管住「有人绕过脚本直接改 JSON」这条路径。
 */
class EvalSetTest {

    private static EvalSet set;

    @BeforeAll
    static void loadOnce() {
        set = EvalSet.load();
    }

    @Test
    @DisplayName("评测集结构自洽，validate() 无错误")
    void structureIsSelfConsistent() {
        assertThat(set.validate()).isEmpty();
    }

    @Test
    @DisplayName("元信息：v1 / FINAL / 500 条 / 语料 19 条 / 线上 top-k=3")
    void metaMatchesExpectation() {
        assertThat(set.version()).isEqualTo("v1");
        assertThat(set.status()).isEqualTo("FINAL");
        assertThat(set.samples()).hasSize(500);
        assertThat(set.rulesIndex()).hasSize(19);
        assertThat(set.corpusSize()).isEqualTo(19);
        assertThat(set.mainK()).isEqualTo(3);
    }

    @Test
    @DisplayName("六类配额与 meta 声明完全一致")
    void categoryQuotaMatches() {
        assertThat(set.declaredQuota())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "direct", 220,
                        "colloquial", 75,
                        "typo", 30,
                        "negation", 50,
                        "cross", 50,
                        "out_of_scope", 75));
        assertThat(set.inScopeSamples()).hasSize(425);
        assertThat(set.outOfScopeSamples()).hasSize(75);
    }

    @Test
    @DisplayName("id 与 query 均唯一（否则同一条会被重复计分）")
    void idsAndQueriesAreUnique() {
        Set<String> ids = new HashSet<>();
        Set<String> queries = new HashSet<>();
        for (EvalSample s : set.samples()) {
            assertThat(ids.add(s.id())).as("重复 id：%s", s.id()).isTrue();
            assertThat(queries.add(s.query())).as("重复 query：%s", s.query()).isTrue();
        }
    }

    @Test
    @DisplayName("非超纲样本的答案都落在真实规则 id 区间内（20~38，不是 1~19）")
    void relevantIdsAreWithinCorpus() {
        for (EvalSample s : set.inScopeSamples()) {
            assertThat(s.relevant()).as("%s 答案为空但非超纲", s.id()).isNotEmpty();
            assertThat(set.ruleIds()).as("%s 的答案越界", s.id()).containsAll(s.relevant());
        }
    }

    @Test
    @DisplayName("超纲样本的答案必须为空，且至少含一条高危关键词样本（near_miss）")
    void outOfScopeAreWellFormed() {
        for (EvalSample s : set.outOfScopeSamples()) {
            assertThat(s.relevant()).as("%s 是超纲题却标了答案", s.id()).isEmpty();
        }
        long nearMiss = set.outOfScopeSamples().stream().filter(s -> s.tagged("near_miss")).count();
        assertThat(nearMiss).as("near_miss 是唯一能暴露「无分数阈值」缺陷的设计，不能为空").isEqualTo(40);
    }

    @Test
    @DisplayName("人工精查子集恰好 120 条，且不变式成立")
    void hardReviewSubsetIsExact() {
        List<EvalSample> hard = set.samples().stream().filter(EvalSample::hardReview).toList();
        assertThat(hard).hasSize(120);
        for (EvalSample s : set.samples()) {
            boolean expected = Set.of("negation", "cross").contains(s.category()) || s.tagged("conflict");
            assertThat(s.hardReview()).as("%s 的 hardReview 不符合不变式", s.id()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("任何标签的样本数都不得低于 5 —— 低于 5 的切片没有统计意义，必须合并或删除")
    void everyTagHasEnoughSamples() {
        for (String tag : set.tagVocabulary()) {
            long n = set.samples().stream().filter(s -> s.tagged(tag)).count();
            assertThat(n).as("标签 %s 只有 %d 条", tag, n).isGreaterThanOrEqualTo(5);
        }
        // omission 是上一版被砍掉的标签，固化下来防止它被重新加回来
        assertThat(set.tagVocabulary()).doesNotContain("omission");
    }

    @Test
    @DisplayName("答案规模上限为 3，因此 oracle 的天花板是 Recall@3 = 1.0")
    void goldSizeNeverExceedsMainK() {
        int max = set.samples().stream().mapToInt(s -> s.relevant().size()).max().orElse(0);
        assertThat(max).as("若 |R| 超过线上 top-k，Recall@k 的天花板本身就不满 1，报告里必须说明").isEqualTo(3);
    }
}
