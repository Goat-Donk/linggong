package com.linggong.ai.eval;

import java.util.List;
import java.util.Set;

/**
 * 评测集里的一条标注样本，字段与 {@code eval-set.json} 一一对应。
 *
 * @param id         稳定主键（q001…），全量生成后不再变动，bad case 归因靠它引用
 * @param query      用户原话，<b>不做任何规范化</b>（口语、错别字、标点都原样保留）
 * @param relevant   标准答案规则 id；<b>空列表表示超纲题</b>，即正确行为是不召回任何规则
 * @param category   单一主考点：direct | colloquial | typo | negation | cross | out_of_scope
 * @param tags       多维难点标记，可为空数组
 * @param hardReview true 表示进入人工精查子集
 * @param note       标注理由，只给人看，评测代码不读
 */
public record EvalSample(String id,
                         String query,
                         List<Long> relevant,
                         String category,
                         List<String> tags,
                         boolean hardReview,
                         String note) {

    public EvalSample {
        relevant = List.copyOf(relevant);
        tags = List.copyOf(tags);
    }

    /** 超纲题：正确行为是「一条都不召回」，主指标不适用。 */
    public boolean outOfScope() {
        return relevant.isEmpty();
    }

    public boolean tagged(String tag) {
        return tags.contains(tag);
    }

    /** 去重后的标准答案集合，供 {@link RankingMetrics} 使用。 */
    public Set<Long> relevantSet() {
        return Set.copyOf(relevant);
    }
}
