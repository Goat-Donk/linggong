package com.linggong.ai.trace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.List;
import java.util.function.Supplier;

/**
 * 把一次 AI 问答包进追踪里，并在流结束时落库。
 *
 * <p>调用方只管把「真正发起问答的那句代码」作为 {@code call} 传进来，其余的交由本类。
 *
 * <h3>两处容易漏掉的落库点</h3>
 * <ul>
 *   <li>{@code call.get()} <b>自己抛异常</b>的分支：那时 Flux 还没造出来，{@code doFinally}
 *       永远不会执行，不显式落库的话这次失败就彻底没记录 —— 而它恰恰是最该被记下来的那种失败
 *       （实测过：账号欠费时 DashScope 同步抛错，trace 表里全靠这一条才留下一排 ERROR）。</li>
 *   <li>{@code doFinally} 里的 {@code CANCEL}：客户端提前断开，流既没完成也没报错，
 *       单独标成 INCOMPLETE，免得和「正常答完」混在一起污染统计。</li>
 * </ul>
 */
@Slf4j
@Component
public class AiChatTracer {

    private final AiTraceRecorder recorder;

    public AiChatTracer(AiTraceRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * 追踪一次问答。
     *
     * @param userId          提问人 id
     * @param userRole        提问人角色（0 打工人 / 1 雇主），同一问题两种角色答案不同，归因时要能分开
     * @param message         用户原始问题
     * @param injectedRuleIds 本次注入提示词的规则 id（全量注入模式下是规则库全集）
     * @param call            真正发起问答的调用
     */
    public Flux<String> traced(Long userId, Integer userRole, String message,
                               List<Long> injectedRuleIds, Supplier<Flux<String>> call) {
        TraceSession session = TraceSession.start(userId, userRole, message);
        session.recordInjectedRules(injectedRuleIds);

        Flux<String> reply;
        try {
            reply = call.get();
        } catch (RuntimeException e) {
            session.markError();
            recorder.persist(session);
            throw e;
        }

        return reply
                .doOnNext(session::appendAnswer)
                .doOnError(e -> session.markError())
                .doFinally(signal -> {
                    if (signal == SignalType.CANCEL) {
                        session.markIncomplete();
                    }
                    recorder.persist(session);
                });
    }
}
