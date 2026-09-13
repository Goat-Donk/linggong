package com.linggong.ai.trace;

import com.linggong.entity.AiTrace;
import com.linggong.mapper.AiTraceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 把追踪会话落成一行 tb_ai_trace。
 *
 * <p><b>唯一职责：无论出什么事都不许把异常抛出去。</b>
 * 本类是在回答流结束的回调里被调用的，此处的异常会直接冒到用户面前 ——
 * 而用户已经拿到完整回答了，却因为「日志表写不进去」看到一条报错，这是不可接受的。
 * 所以落库失败只打 ERROR 日志（埋点的目的是排查问题，埋点自己坏了必须让人知道），
 * 但绝不上抛。
 *
 * <p>与 {@code JobApplicationConsumer#notifyApplyFailed} 是同一条思路：
 * 附属动作失败不能反过来影响主流程。
 */
@Slf4j
@Service
public class AiTraceRecorder {

    private final AiTraceMapper aiTraceMapper;

    public AiTraceRecorder(AiTraceMapper aiTraceMapper) {
        this.aiTraceMapper = aiTraceMapper;
    }

    /**
     * 落库。失败仅告警。
     *
     * @param session 追踪会话；为 null（未开启追踪）时静默返回
     */
    public void persist(TraceSession session) {
        if (session == null) {
            return;
        }
        try {
            AiTrace trace = session.finish();
            aiTraceMapper.insert(trace);
            log.debug("AI 问答 trace 已落库：id={}, status={}, 注入规则 {}",
                    trace.getTraceId(), trace.getStatus(), trace.getInjectedRuleIds());
        } catch (Exception e) {
            // 表还没建、DB 抖动、字段超长都可能走到这里。埋点坏了不该让用户看见。
            log.error("AI 问答 trace 落库失败（不影响问答本身）", e);
        }
    }
}
