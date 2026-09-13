package com.linggong.ai.trace;

import com.linggong.entity.AiTrace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link AiChatTracer} 的时序契约。
 *
 * <p>这个类的价值全在「什么时候发生什么」上，所以测试也全部围绕时序写 ——
 * 尤其是两条最容易漏掉的落库路径：{@code call.get()} 自己抛异常、以及流的 CANCEL 信号。
 */
class AiChatTracerTest {

    /** 全量注入模式下就是规则库全集，测试里用两条够表达语义 */
    private static final List<Long> RULES = List.of(20L, 21L);

    private final AiTraceRecorder recorder = mock(AiTraceRecorder.class);
    private final AiChatTracer tracer = new AiChatTracer(recorder);

    /** 取回落库时被捕获的会话，转成实体好断言。 */
    private AiTrace persistedTrace() {
        ArgumentCaptor<TraceSession> captor = ArgumentCaptor.forClass(TraceSession.class);
        verify(recorder).persist(captor.capture());
        return captor.getValue().finish();
    }

    @Test
    @DisplayName("正常答完：回答被累积，状态 OK，注入的规则一并落库")
    void accumulatesAnswer() {
        tracer.traced(7L, 0, "服务费怎么算", RULES, () -> Flux.just("平台", "收 ", "10%")).blockLast();

        AiTrace trace = persistedTrace();
        assertThat(trace.getFinalAnswer()).isEqualTo("平台收 10%");
        assertThat(trace.getStatus()).isEqualTo(AiTrace.STATUS_OK);
        assertThat(trace.getUserId()).isEqualTo(7L);
        assertThat(trace.getUserRole()).isZero();
        assertThat(trace.getQuery()).isEqualTo("服务费怎么算");
        assertThat(trace.getInjectedRuleIds()).isEqualTo("[20,21]");
    }

    @Test
    @DisplayName("call.get() 自己抛异常：Flux 根本没造出来，也必须落一条 ERROR 记录")
    void callThrowsIsStillRecorded() {
        assertThatThrownBy(() -> tracer.traced(7L, 0, "q", RULES, () -> {
            throw new IllegalStateException("AiService 装配失败");
        })).isInstanceOf(IllegalStateException.class);

        AiTrace trace = persistedTrace();
        assertThat(trace.getStatus()).isEqualTo(AiTrace.STATUS_ERROR);
        assertThat(trace.getFinalAnswer()).as("还没开始答，回答该是空的").isEmpty();
    }

    @Test
    @DisplayName("流内异常：标记 ERROR 后落库（此时前端会拿到兜底话术）")
    void streamErrorIsRecorded() {
        List<String> received = new ArrayList<>();
        tracer.traced(7L, 0, "q", RULES,
                        () -> Flux.concat(Flux.just("半句"), Flux.error(new RuntimeException("模型断了"))))
                .onErrorResume(e -> Flux.just("兜底话术"))
                .doOnNext(received::add)
                .blockLast();

        assertThat(received).containsExactly("半句", "兜底话术");
        AiTrace trace = persistedTrace();
        assertThat(trace.getStatus()).isEqualTo(AiTrace.STATUS_ERROR);
        assertThat(trace.getFinalAnswer())
                .as("只记模型真正吐出来的部分，不把兜底话术混进回答")
                .isEqualTo("半句");
    }

    @Test
    @DisplayName("客户端提前断开：既没答完也没报错，标成 INCOMPLETE")
    void cancelIsIncomplete() {
        tracer.traced(7L, 0, "q", RULES, () -> Flux.just("一", "二", "三"))
                .take(1)
                .blockLast();

        assertThat(persistedTrace().getStatus()).isEqualTo(AiTrace.STATUS_INCOMPLETE);
    }

    @Test
    @DisplayName("每次问答恰好落一条，且 traceId 各不相同")
    void oneRowPerChat() {
        tracer.traced(7L, 0, "q1", RULES, () -> Flux.just("a")).blockLast();
        tracer.traced(7L, 0, "q2", RULES, () -> Flux.just("b")).blockLast();

        ArgumentCaptor<TraceSession> captor = ArgumentCaptor.forClass(TraceSession.class);
        verify(recorder, times(2)).persist(captor.capture());
        assertThat(captor.getAllValues().stream().map(TraceSession::traceId).distinct()).hasSize(2);
    }

    @Test
    @DisplayName("空 Flux（一片都没吐）：仍然落库，首字延迟为 null")
    void emptyStreamStillPersists() {
        tracer.traced(7L, 0, "q", RULES, () -> Flux.empty()).blockLast();

        AiTrace trace = persistedTrace();
        assertThat(trace.getFinalAnswer()).isEmpty();
        assertThat(trace.getLatencyBreakdown()).contains("\"firstTokenMs\":null");
    }
}
