package com.linggong.ai.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linggong.entity.AiTrace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TraceSession} 的行为契约：跨两条线程逐步填充，最后产出一行可落库的 trace。
 */
class TraceSessionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @AfterEach
    void tearDown() {
        TraceSession.end();
    }

    @Test
    @DisplayName("begin 挂到当前线程，end 摘掉；end 幂等")
    void threadLocalLifecycle() {
        assertThat(TraceSession.current()).isNull();
        TraceSession session = TraceSession.begin(7L, 0, "押金压多少");
        assertThat(TraceSession.current()).isSameAs(session);
        TraceSession.end();
        assertThat(TraceSession.current()).isNull();
        TraceSession.end();
        assertThat(TraceSession.current()).isNull();
    }

    @Test
    @DisplayName("traceId 是 32 位无横线 UUID，两次会话不重复")
    void traceIdShape() {
        TraceSession a = TraceSession.begin(1L, 0, "q");
        String first = a.traceId();
        TraceSession.end();
        TraceSession b = TraceSession.begin(1L, 0, "q");
        assertThat(first).hasSize(32).doesNotContain("-").isNotEqualTo(b.traceId());
    }

    @Test
    @DisplayName("检索结果原样记录，零召回序列化成 [] 而不是 null")
    void recordsRetrieval() throws Exception {
        TraceSession session = TraceSession.begin(1L, 0, "服务费怎么算");
        session.recordRetrieval(List.of(24L, 28L), 3_000_000L);

        AiTrace trace = session.finish();
        assertThat(trace.getRetrievedRuleIds()).isEqualTo("[24,28]");

        JsonNode latency = MAPPER.readTree(trace.getLatencyBreakdown());
        assertThat(latency.get("bm25Ms").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("零召回写成 []，与「没记录」区分开")
    void zeroRecallIsAValue() {
        TraceSession session = TraceSession.begin(1L, 0, "今天天气怎么样");
        session.recordRetrieval(List.of(), 1_000_000L);
        assertThat(session.finish().getRetrievedRuleIds()).isEqualTo("[]");
    }

    @Test
    @DisplayName("检索抛异常 → 只留痕，不产出规则 id")
    void retrievalFailureLeavesMark() throws Exception {
        TraceSession session = TraceSession.begin(1L, 0, "q");
        session.recordRetrievalFailure(2_000_000L);

        AiTrace trace = session.finish();
        assertThat(trace.getRetrievedRuleIds()).isEqualTo("[]");
        JsonNode latency = MAPPER.readTree(trace.getLatencyBreakdown());
        assertThat(latency.get("retrievalFailed").asBoolean()).isTrue();
        assertThat(latency.get("bm25Ms").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("latency_breakdown 带齐用户定的三个阶段；未启用的显式为 null（不能是 0）")
    void latencySegmentsShape() throws Exception {
        TraceSession session = TraceSession.begin(1L, 0, "q");
        session.recordRetrieval(List.of(1L), 1_000_000L);
        session.appendAnswer("好");

        JsonNode latency = MAPPER.readTree(session.finish().getLatencyBreakdown());
        assertThat(latency.has("queryRewriteMs")).isTrue();
        assertThat(latency.has("bm25Ms")).isTrue();
        assertThat(latency.has("rerankMs")).isTrue();
        assertThat(latency.get("queryRewriteMs").isNull())
                .as("查询改写尚未接入，必须是 null 而非 0").isTrue();
        assertThat(latency.get("rerankMs").isNull())
                .as("重排尚未接入，必须是 null 而非 0").isTrue();
        assertThat(latency.has("firstTokenMs")).isTrue();
        assertThat(latency.has("totalMs")).isTrue();
        assertThat(latency.has("retrievalFailed")).as("没失败就不该出现这个键").isFalse();
    }

    @Test
    @DisplayName("首字延迟只记第一片，后续分片不再刷新")
    void firstTokenRecordedOnce() throws Exception {
        TraceSession session = TraceSession.begin(1L, 0, "q");
        assertThat(MAPPER.readTree(session.finish().getLatencyBreakdown()).get("firstTokenMs").isNull())
                .as("一片都没收到时首字延迟应为 null").isTrue();

        session.appendAnswer("你");
        session.appendAnswer("好");
        session.appendAnswer("呀");
        AiTrace trace = session.finish();
        assertThat(trace.getFinalAnswer()).isEqualTo("你好呀");
        assertThat(MAPPER.readTree(trace.getLatencyBreakdown()).get("firstTokenMs").isNumber()).isTrue();
    }

    @Test
    @DisplayName("空分片不算首字（有些模型会先吐空串）")
    void blankChunkDoesNotCountAsFirstToken() throws Exception {
        TraceSession session = TraceSession.begin(1L, 0, "q");
        session.appendAnswer("");
        session.appendAnswer(null);
        assertThat(MAPPER.readTree(session.finish().getLatencyBreakdown()).get("firstTokenMs").isNull())
                .isTrue();
    }

    @Test
    @DisplayName("回答超长按上限截断，且不把代理对劈成半个字符")
    void answerTruncation() {
        TraceSession plain = TraceSession.begin(1L, 0, "q");
        plain.appendAnswer("啊".repeat(TraceSession.MAX_ANSWER_CHARS + 500));
        assertThat(plain.finish().getFinalAnswer())
                .hasSize(TraceSession.MAX_ANSWER_CHARS + "……（已截断）".length())
                .endsWith("……（已截断）");

        // 一个 emoji 占两个 char：正好卡在边界上时，宁可少存一个字符也不能存半个
        TraceSession surrogate = TraceSession.begin(1L, 0, "q");
        surrogate.appendAnswer("a".repeat(TraceSession.MAX_ANSWER_CHARS - 1) + "😀😀");
        String answer = surrogate.finish().getFinalAnswer();
        assertThat(answer.substring(0, answer.length() - "……（已截断）".length()))
                .hasSize(TraceSession.MAX_ANSWER_CHARS - 1);
    }

    @Test
    @DisplayName("状态：异常优先，「没答完」不许盖掉真正的错误")
    void statusPrecedence() {
        TraceSession ok = TraceSession.begin(1L, 0, "q");
        assertThat(ok.finish().getStatus()).isEqualTo(AiTrace.STATUS_OK);

        TraceSession cancelled = TraceSession.begin(1L, 0, "q");
        cancelled.markIncomplete();
        assertThat(cancelled.finish().getStatus()).isEqualTo(AiTrace.STATUS_INCOMPLETE);

        TraceSession errored = TraceSession.begin(1L, 0, "q");
        errored.markError();
        errored.markIncomplete();
        assertThat(errored.finish().getStatus())
                .as("已经出错了，就不能被「客户端断开」覆盖成 INCOMPLETE").isEqualTo(AiTrace.STATUS_ERROR);
    }
}
