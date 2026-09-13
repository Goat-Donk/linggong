package com.linggong.ai.trace;

import com.linggong.entity.AiTrace;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TracingRuleRetriever} 的装饰器契约：透明、留痕、异常照抛。
 */
class TracingRuleRetrieverTest {

    @AfterEach
    void tearDown() {
        TraceSession.end();
    }

    private static Content content(String ruleId) {
        return Content.from(TextSegment.from("【规则】正文",
                Metadata.from(Map.of("ruleId", ruleId, "title", "标题" + ruleId))));
    }

    @Test
    @DisplayName("透明：返回的就是下游那批 Content，顺序与对象都不变")
    void passThroughUnchanged() {
        List<Content> expected = List.of(content("24"), content("28"));
        TracingRuleRetriever retriever = new TracingRuleRetriever(q -> expected);
        TraceSession.begin(1L, 0, "q");

        List<Content> actual = retriever.retrieve(Query.from("q"));

        assertThat(actual).isSameAs(expected);
    }

    @Test
    @DisplayName("留痕：把命中的规则 id 与耗时写进当前会话")
    void recordsRuleIdsAndLatency() throws Exception {
        TracingRuleRetriever retriever = new TracingRuleRetriever(
                q -> List.of(content("24"), content("28")));
        TraceSession session = TraceSession.begin(1L, 0, "q");

        retriever.retrieve(Query.from("q"));

        AiTrace trace = session.finish();
        assertThat(trace.getRetrievedRuleIds()).isEqualTo("[24,28]");
        assertThat(trace.getLatencyBreakdown()).contains("\"bm25Ms\"");
    }

    @Test
    @DisplayName("没有会话时静默透传，不抛错也不留副作用（A-3 评测会直接 new 生产检索器）")
    void worksWithoutSession() {
        AtomicInteger calls = new AtomicInteger();
        List<Content> expected = List.of(content("20"));
        TracingRuleRetriever retriever = new TracingRuleRetriever(q -> {
            calls.incrementAndGet();
            return expected;
        });
        assertThat(TraceSession.current()).isNull();

        assertThat(retriever.retrieve(Query.from("q"))).isSameAs(expected);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("下游抛异常：原样上抛，同时标记 retrievalFailed —— 不许吞成「零召回」")
    void propagatesFailure() throws Exception {
        TracingRuleRetriever retriever = new TracingRuleRetriever(q -> {
            throw new IllegalStateException("DB 挂了");
        });
        TraceSession session = TraceSession.begin(1L, 0, "q");

        assertThatThrownBy(() -> retriever.retrieve(Query.from("q")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB 挂了");

        AiTrace trace = session.finish();
        assertThat(trace.getRetrievedRuleIds()).as("失败不能伪装成一次正常拒答").isEqualTo("[]");
        assertThat(trace.getLatencyBreakdown()).contains("\"retrievalFailed\":true");
    }

    @Test
    @DisplayName("元数据缺失/非数字：跳过该条而不是抛错，检索本身是成功的")
    void toleratesBadMetadata() {
        List<Content> contents = List.of(
                content("24"),
                Content.from(TextSegment.from("没有 ruleId 的段落")),
                content("不是数字"));
        TracingRuleRetriever retriever = new TracingRuleRetriever(q -> contents);
        TraceSession session = TraceSession.begin(1L, 0, "q");

        assertThat(retriever.retrieve(Query.from("q"))).isSameAs(contents);
        assertThat(session.finish().getRetrievedRuleIds())
                .as("三条里只有一条元数据可用").isEqualTo("[24]");
    }

    @Test
    @DisplayName("空语料/零召回：记录成 []，这是有意义的值")
    void emptyResultIsRecorded() {
        TracingRuleRetriever retriever = new TracingRuleRetriever(q -> List.of());
        TraceSession session = TraceSession.begin(1L, 0, "q");
        retriever.retrieve(Query.from("q"));
        assertThat(session.finish().getRetrievedRuleIds()).isEqualTo("[]");
    }
}
