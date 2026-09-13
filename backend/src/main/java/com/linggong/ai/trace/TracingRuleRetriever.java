package com.linggong.ai.trace;

import com.linggong.ai.rule.RuleContentRetriever;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索器追踪装饰器：量耗时、抓命中规则 id，再原样透传给真正的检索实现。
 *
 * <h3>为什么是装饰器，而不是把埋点写进 Bm25ContentRetriever</h3>
 * 检索实现会换（规划里 C 阶段要加向量召回 + RRF 融合）。埋点写进实现类，换实现就得重写一遍；
 * 写成装饰器，则<b>将来不管换成几路召回，追踪都自动跟着走</b>。
 *
 * <h3>它同时是 C0 说的那道「门面」</h3>
 * 此前 {@code @AiService} 直接按 bean 名绑死 {@code ruleBm25Retriever}，换检索策略就得改注解。
 * 现在绑的是本类的固定名 {@code ruleRetriever}，真正的实现由构造参数决定：
 * C 阶段接混合检索时，只改这一处注入，问答链路与追踪都不动。
 *
 * <h3>三条不容妥协的性质</h3>
 * <ol>
 *   <li><b>返回结果一个字节都不改</b>。元数据里的 {@code ruleId}/{@code title} 是 trace 与评测的共同依赖，
 *       装饰器只读不写；</li>
 *   <li><b>异常原样上抛</b>。检索失败就是失败，埋点不许把它吞成「零召回」——
 *       那会把一个故障伪装成一次正常的拒答，是最难查的那种 bug；</li>
 *   <li><b>没有会话时静默透传</b>。评测（A-3 的 B2/B3/B4）会直接 new 生产检索器，
 *       根本不经过这里；被测到也不该有任何副作用。</li>
 * </ol>
 */
@Slf4j
@Component("ruleRetriever")
@Primary
public class TracingRuleRetriever implements RuleContentRetriever {

    private final RuleContentRetriever delegate;

    public TracingRuleRetriever(@Qualifier("ruleBm25ContentRetriever") RuleContentRetriever delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Content> retrieve(Query query) {
        TraceSession session = TraceSession.current();
        if (session == null) {
            // 设计前提是「检索与调用方同线程」，拿不到会话说明前提被破坏了（多半是有人把检索改成异步）。
            // 打 WARN 而不是静默放过：不然 trace 会安静地少掉整个检索段，而报告上看起来只是「没检索到东西」。
            log.warn("RAG 检索发生在未追踪的线程上，本次 trace 将丢失检索段。query={}", query == null ? null : query.text());
            return delegate.retrieve(query);
        }
        long start = System.nanoTime();
        List<Content> contents;
        try {
            contents = delegate.retrieve(query);
        } catch (RuntimeException e) {
            session.recordRetrievalFailure(System.nanoTime() - start);
            throw e;
        }
        session.recordRetrieval(ruleIdsOf(contents), System.nanoTime() - start);
        return contents;
    }

    /**
     * 从检索结果里抠出规则 id。
     *
     * <p>取不到时<b>跳过而不是抛错</b>：检索本身是成功的，坏的只是元数据，为一个埋点字段
     * 把用户的正常回答打断是本末倒置。但也确实打 WARN —— 系统性缺元数据会让所有 trace
     * 都写成 {@code []}，那看起来跟「一次都没检索到」一模一样，必须留痕。
     */
    private static List<Long> ruleIdsOf(List<Content> contents) {
        List<Long> ids = new ArrayList<>(contents.size());
        for (Content content : contents) {
            String ruleId = content.textSegment().metadata().getString("ruleId");
            if (ruleId == null) {
                log.warn("检索结果缺少 ruleId 元数据，该条不计入 trace。title={}",
                        content.textSegment().metadata().getString("title"));
                continue;
            }
            try {
                ids.add(Long.valueOf(ruleId));
            } catch (NumberFormatException e) {
                log.warn("检索结果的 ruleId 不是数字，该条不计入 trace。ruleId={}", ruleId);
            }
        }
        return ids;
    }
}
