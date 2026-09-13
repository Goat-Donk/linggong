package com.linggong.ai.eval;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.linggong.ai.rule.impl.Bm25ContentRetriever;
import com.linggong.entity.AiRule;
import com.linggong.mapper.AiRuleMapper;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.mockito.Mockito;

import java.util.List;

/**
 * 用<b>真实生产检索器</b> {@link Bm25ContentRetriever} 构造评测用的 {@link RankingRetriever}。
 *
 * <p>这是 A-3 最关键的一个决定。评测 B2/B3/B4 有两条路：
 * <ol>
 *   <li>在测试里照抄一份 BM25 —— 那么基线数字描述的是<b>测试的实现</b>，不是生产的实现，
 *       整条证据链当场断掉。以后生产改一行，评测还浑然不觉；</li>
 *   <li>驱动生产类本身 —— 数字就是生产本身的数字。</li>
 * </ol>
 * 这里选第二条。做法是给 {@link AiRuleMapper} 打桩：它只是个
 * {@code BaseMapper<AiRule>} 接口，Mockito 拦下 {@code selectList} 返回我们准备的语料即可，
 * 于是<b>不启 Spring、不连 DB</b>，生产代码一行都不用改。
 *
 * <p>打分器不变、只换语料，就让 B2→B3→B4 的每一步都只隔离出一个变量。
 */
public final class ProductionBm25 {

    private ProductionBm25() {
    }

    /**
     * 构造一个跑在给定语料上的生产检索器。
     *
     * @param rules 喂给生产检索器的语料（B2 原始 / B3 tags 放大 / B4 加别名）
     * @param topK  线上是 3；评测跑 10 以便得到 k 敏感度曲线（见 {@link #of}）
     */
    public static Bm25ContentRetriever retriever(List<AiRule> rules, int topK) {
        AiRuleMapper mapper = Mockito.mock(AiRuleMapper.class);
        Mockito.when(mapper.selectList(Mockito.<Wrapper<AiRule>>any())).thenReturn(rules);
        Bm25ContentRetriever retriever = new Bm25ContentRetriever(mapper, topK);
        retriever.loadRules();
        verifyLoaded(retriever, rules);
        return retriever;
    }

    /**
     * 行为探针：确认语料真的进了索引。
     *
     * <p>为什么不给生产加一个 {@code isLoaded()} getter：为了评测放宽生产 API 是没必要的让步。
     * 而且读标志位只能证明「赋值跑了」，用第一条规则的标题去检索并断言有命中，
     * 证明的是「打分链路真的能工作」—— 后者才是评测数字有效的前提。
     * {@code loadRules()} 内部 catch 了异常并退化为空索引，没有这道探针，一个打桩失败的
     * mock 会让 B2/B3/B4 全部静默得 0 分，而 0 分在报告里看起来像是「方案很差」而非「评测坏了」。
     */
    private static void verifyLoaded(Bm25ContentRetriever retriever, List<AiRule> rules) {
        String probe = rules.get(0).getTitle();
        if (retriever.retrieve(Query.from(probe)).isEmpty()) {
            throw new IllegalStateException(
                    "生产检索器载入后对自身规则标题「" + probe + "」检索为空，索引未建立");
        }
    }

    /**
     * 评测入口：返回 id 排序列表。
     *
     * <p><b>为什么评测跑 topK=10 而线上是 3</b>：要出 k=1/3/5/10 的敏感度曲线，
     * 检索器至少得返回 10 条。这不影响 k≤3 各列与线上一致 —— 生产 {@code retrieve()}
     * 是按分数降序取前 k 名，前 3 名不会因为多取了 7 条而改变。
     * 这条等价关系由 {@code BaselineLadderTest} 的用例实际验证，不是口头保证。
     */
    public static RankingRetriever of(List<AiRule> rules, int topK) {
        Bm25ContentRetriever retriever = retriever(rules, topK);
        return query -> retriever.retrieve(Query.from(query)).stream()
                .map(ProductionBm25::ruleIdOf)
                .toList();
    }

    /** 从生产返回的 {@link Content} 元数据里取规则 id —— 生产已在 Metadata 里放了 ruleId。 */
    private static Long ruleIdOf(Content content) {
        String ruleId = content.textSegment().metadata().getString("ruleId");
        if (ruleId == null) {
            throw new IllegalStateException("生产检索器返回的 Content 缺少 ruleId 元数据，评测无法归约");
        }
        return Long.valueOf(ruleId);
    }
}
