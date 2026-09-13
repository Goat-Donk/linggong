package com.linggong.ai.eval;

import java.util.List;

/**
 * 评测用的检索器抽象 —— 整个评测框架唯一的可插拔缝。
 *
 * <p>评测只要求实现方回答一个问题：「给你这句用户原话，你按相关性降序返回哪些规则 id」。
 * 至于内部是朴素 contains、纯 TF、BM25、tags 加权，还是日后的向量 + RRF 混合检索，
 * 评测框架完全不关心 —— A-3 阶段的 B0~B4 五个方案、C 阶段的混合检索，
 * 都只是这个接口的不同实现，跑的是同一份 500 条数据、同一套指标。
 *
 * <p><b>契约</b>：
 * <ul>
 *   <li>返回列表不可为 {@code null}，不召回时返回空列表；</li>
 *   <li>不可含重复 id，也不可含语料里不存在的 id（幻觉 id）——
 *       {@link EvalReport#evaluate} 会主动校验并抛错，而不是把错误摊平成「分数低一点」；</li>
 *   <li>返回条数可以少于 k：生产检索器在命中分数归零时会提前 break，这是正确行为，不是缺陷。</li>
 * </ul>
 */
@FunctionalInterface
public interface RankingRetriever {

    List<Long> retrieve(String query);
}
