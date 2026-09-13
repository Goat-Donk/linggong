package com.linggong.ai.trace;

import com.linggong.entity.AiTrace;
import com.linggong.mapper.AiTraceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiTraceRecorder} 的唯一契约：<b>无论出什么事都不许把异常抛出去</b>。
 *
 * <p>它跑在回答流结束的回调里，此处的异常会直接冒到用户面前 —— 用户已经拿到完整回答了，
 * 却因为「日志表写不进去」看到一条报错。所以这里的测试几乎全是「注入各种故障，断言不抛」。
 */
class AiTraceRecorderTest {

    private final AiTraceMapper mapper = mock(AiTraceMapper.class);
    private final AiTraceRecorder recorder = new AiTraceRecorder(mapper);

    @Test
    @DisplayName("正常路径：落一行，字段都搬对了")
    void persistsTrace() {
        TraceSession session = TraceSession.start(7L, 1, "押金压多少");
        session.recordInjectedRules(List.of(22L));
        session.appendAnswer("押金是……");

        recorder.persist(session);

        ArgumentCaptor<AiTrace> captor = ArgumentCaptor.forClass(AiTrace.class);
        verify(mapper).insert(captor.capture());
        AiTrace saved = captor.getValue();
        assertThat(saved.getTraceId()).isEqualTo(session.traceId());
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getUserRole()).isEqualTo(1);
        assertThat(saved.getQuery()).isEqualTo("押金压多少");
        assertThat(saved.getInjectedRuleIds()).isEqualTo("[22]");
        assertThat(saved.getFinalAnswer()).isEqualTo("押金是……");
        assertThat(saved.getStatus()).isEqualTo(AiTrace.STATUS_OK);
    }

    @Test
    @DisplayName("DB 挂了：只打日志，异常绝不外抛")
    void neverPropagatesDbFailure() {
        when(mapper.insert(any(AiTrace.class)))
                .thenThrow(new DataAccessResourceFailureException("DB 挂了"));
        TraceSession session = TraceSession.start(7L, 0, "q");
        session.appendAnswer("答完了");

        assertThatCode(() -> recorder.persist(session)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("表还没建（SQL 异常）：同样不外抛 —— 这是最可能真实发生的场景")
    void neverPropagatesMissingTable() {
        when(mapper.insert(any(AiTrace.class)))
                .thenThrow(new RuntimeException("Table 'linggong.tb_ai_trace' doesn't exist"));
        assertThatCode(() -> recorder.persist(TraceSession.start(7L, 0, "q")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("未开启追踪（session 为 null）：空操作，连 mapper 都不碰")
    void nullSessionIsNoOp() {
        recorder.persist(null);
        verify(mapper, never()).insert(any(AiTrace.class));
    }
}
