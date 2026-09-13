package com.linggong.ai.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 三个「不算真方案」的参照检索器，用途是给指标引擎做标定。
 *
 * <p>它们的价值不在评测检索质量，而在于：如果 {@link EvalReport} 对这三个已知答案的检索器
 * 算出的数字不符合预期，那问题一定出在指标引擎或数据本身，而不是被评测的检索方案。
 * 这是「先证明尺子是准的，再去量东西」。
 */
public final class SanityRetrievers {

    private SanityRetrievers() {
    }

    /**
     * 上限参照：直接把标准答案原样返回。
     *
     * <p>它的意义是给出每个指标的<b>理论上界</b>。注意上界并不都是 1：
     * 当 {@code |R| > k} 时 Recall@k 与 NDCG@k 必然小于 1（比如答案要 3 条规则而只看前 1 条），
     * 这部分「天花板本身就不满」的差距不能算到检索方案头上。
     */
    public static RankingRetriever oracle(EvalSet set) {
        Map<String, List<Long>> gold = new HashMap<>();
        for (EvalSample s : set.samples()) {
            gold.put(s.query(), s.relevant());
        }
        // 故意不兜底 null：query 对不上说明评测集与调用方错位了，应该让 EvalReport 立刻报错
        return gold::get;
    }

    /**
     * 下限参照：永远不召回。
     *
     * <p>它同时演示了本评测框架最重要的一条设计约束 —— <b>两组指标必须一起看</b>：
     * 该策略把「拒答准确率」刷到 100%（超纲题全部正确拒答），主指标却全是 0。
     * 反过来 oracle 能把主指标拉满。任何只看单组指标的验收口径都能被平凡策略刷满。
     */
    public static RankingRetriever alwaysEmpty() {
        return query -> List.of();
    }

    /**
     * 随机基线：把全部规则打乱后返回，命中纯靠位置运气。
     *
     * <p>种子固定且按 query 派生，因此结果完全可复现 —— 评测报告里的参照线不能每次都变。
     */
    public static RankingRetriever random(EvalSet set, long seed) {
        List<Long> pool = new ArrayList<>(set.ruleIds());
        return query -> {
            List<Long> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled, new Random(seed + query.hashCode()));
            return shuffled;
        };
    }
}
