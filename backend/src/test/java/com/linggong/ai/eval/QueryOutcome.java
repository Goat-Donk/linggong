package com.linggong.ai.eval;

import java.util.List;
import java.util.Set;

/**
 * 一条样本的评测结果：标准答案 + 检索器实际返回的排序。
 *
 * @param sample    原始标注样本（保留 id / category / tags，供切片分析）
 * @param relevant  去重后的标准答案集合
 * @param retrieved 检索器返回的规则 id，按相关性降序
 */
public record QueryOutcome(EvalSample sample, Set<Long> relevant, List<Long> retrieved) {

    public QueryOutcome {
        retrieved = List.copyOf(retrieved);
    }

    public boolean outOfScope() {
        return relevant.isEmpty();
    }

    /** 一条都没召回 —— 对超纲题是正确行为，对有答案的题是零分。 */
    public boolean emptyRetrieval() {
        return retrieved.isEmpty();
    }

    public String id() {
        return sample.id();
    }

    public String category() {
        return sample.category();
    }

    public List<String> tags() {
        return sample.tags();
    }

    public boolean hardReview() {
        return sample.hardReview();
    }
}
