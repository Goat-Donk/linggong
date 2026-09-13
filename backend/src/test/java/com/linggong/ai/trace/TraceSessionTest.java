package com.linggong.ai.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linggong.entity.AiTrace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TraceSession} 的行为契约：跨两条线程逐步填充，最后产出一行可落库的 trace。
 */
class TraceSessionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("traceId 是 32 位无横线 UUID，两次会话不重复")
    void traceIdShape() {
        TraceSession a = TraceSession.start(1L, 0, "q");
        String first = a.traceId();
        TraceSession b = TraceSession.start(1L, 0, "q");
        assertThat(first).hasSize(32).doesNotContain("-").isNotEqualTo(b.traceId());
    }

    @Test
    @DisplayName("注入的规则 id 原样记录（全量注入模式下是规则库全集）")
    void recordsInjectedRules() {
        TraceSession session = TraceSession.start(1L, 0, "服务费怎么算");
        session.recordInjectedRules(List.of(20L, 21L, 22L));
        assertThat(session.finish().getInjectedRuleIds()).isEqualTo("[20,21,22]");
    }

    @Test
    @DisplayName("规则库为空时写成 []，与「没记录」区分开")
    void emptyRuleBookIsAValue() {
        TraceSession session = TraceSession.start(1L, 0, "q");
        session.recordInjectedRules(List.of());
        assertThat(session.finish().getInjectedRuleIds()).isEqualTo("[]");
    }

    @Test
    @DisplayName("传 null 不覆盖已有记录（保证调用方漏传时不会把已写的数据抹掉）")
    void nullRuleIdsDoesNotClobber() {
        TraceSession session = TraceSession.start(1L, 0, "q");
        session.recordInjectedRules(List.of(7L));
        session.recordInjectedRules(null);
        assertThat(session.finish().getInjectedRuleIds()).isEqualTo("[7]");
    }

    @Test
    @DisplayName("latency_breakdown 只含 firstTokenMs / totalMs；RAG 时代的键必须已消失")
    void latencySegmentsShape() throws Exception {
        TraceSession session = TraceSession.start(1L, 0, "q");
        session.appendAnswer("好");

        JsonNode latency = MAPPER.readTree(session.finish().getLatencyBreakdown());
        assertThat(latency.has("firstTokenMs")).isTrue();
        assertThat(latency.has("totalMs")).isTrue();
        // 回归护栏：改成全量注入后，检索相关的耗时段不该再出现
        assertThat(latency.has("bm25Ms")).as("bm25Ms 是检索时代的字段，不该复活").isFalse();
        assertThat(latency.has("queryRewriteMs")).isFalse();
        assertThat(latency.has("rerankMs")).isFalse();
        assertThat(latency.has("retrievalFailed")).isFalse();
    }

    @Test
    @DisplayName("首字延迟只记第一片，后续分片不再刷新")
    void firstTokenRecordedOnce() throws Exception {
        TraceSession session = TraceSession.start(1L, 0, "q");
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
        TraceSession session = TraceSession.start(1L, 0, "q");
        session.appendAnswer("");
        session.appendAnswer(null);
        assertThat(MAPPER.readTree(session.finish().getLatencyBreakdown()).get("firstTokenMs").isNull())
                .isTrue();
    }

    @Test
    @DisplayName("回答超长按上限截断，且不把代理对劈成半个字符")
    void answerTruncation() {
        TraceSession plain = TraceSession.start(1L, 0, "q");
        plain.appendAnswer("啊".repeat(TraceSession.MAX_ANSWER_CHARS + 500));
        assertThat(plain.finish().getFinalAnswer())
                .hasSize(TraceSession.MAX_ANSWER_CHARS + "……（已截断）".length())
                .endsWith("……（已截断）");

        // 一个 emoji 占两个 char：正好卡在边界上时，宁可少存一个字符也不能存半个
        TraceSession surrogate = TraceSession.start(1L, 0, "q");
        surrogate.appendAnswer("a".repeat(TraceSession.MAX_ANSWER_CHARS - 1) + "😀😀");
        String answer = surrogate.finish().getFinalAnswer();
        assertThat(answer.substring(0, answer.length() - "……（已截断）".length()))
                .hasSize(TraceSession.MAX_ANSWER_CHARS - 1);
    }

    @Test
    @DisplayName("状态：异常优先，「没答完」不许盖掉真正的错误")
    void statusPrecedence() {
        TraceSession ok = TraceSession.start(1L, 0, "q");
        assertThat(ok.finish().getStatus()).isEqualTo(AiTrace.STATUS_OK);

        TraceSession cancelled = TraceSession.start(1L, 0, "q");
        cancelled.markIncomplete();
        assertThat(cancelled.finish().getStatus()).isEqualTo(AiTrace.STATUS_INCOMPLETE);

        TraceSession errored = TraceSession.start(1L, 0, "q");
        errored.markError();
        errored.markIncomplete();
        assertThat(errored.finish().getStatus())
                .as("已经出错了，就不能被「客户端断开」覆盖成 INCOMPLETE").isEqualTo(AiTrace.STATUS_ERROR);
    }
}
