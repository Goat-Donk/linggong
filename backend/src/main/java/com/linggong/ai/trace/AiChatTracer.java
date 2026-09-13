package com.linggong.ai.trace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.function.Supplier;

/**
 * 把一次 AI 问答包进追踪里，并在流结束时落库。
 *
 * <p>调用方只管把「真正发起问答的那句代码」作为 {@code call} 传进来，其余的交由本类。
 *
 * <h3>时序为什么必须是这样</h3>
 * <pre>
 * begin()  →  call.get()  →  end()  →  return 组装好的 Flux
 *             ↑ 检索同步发生在这里（同线程）
 * </pre>
 * {@code end()} 紧跟在 {@code call.get()} 之后、返回 Flux 之前，而不是等流结束 ——
 * 因为会话绑定的是「当前线程这一小段同步执行」，检索做完就该摘掉 ThreadLocal，
 * 否则线程被复用时会串到下一个请求上。
 *
 * <p>{@code call.get()} 自己抛异常的分支单独处理：那时 Flux 还没造出来，{@code doFinally}
 * 永远不会执行，不显式落库的话这次失败就彻底没记录 —— 而它恰恰是最该被记下来的那种失败。
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
     * @param userId   提问人 id
     * @param userRole 提问人角色（0 打工人 / 1 雇主），同一问题两种角色答案不同，归因时要能分开
     * @param message  用户原始问题
     * @param call     真正发起问答的调用，检索会在其内部同步发生
     */
    public Flux<String> traced(Long userId, Integer userRole, String message, Supplier<Flux<String>> call) {
        TraceSession session = TraceSession.begin(userId, userRole, message);
        Flux<String> reply;
        try {
            reply = call.get();
        } catch (RuntimeException e) {
            TraceSession.end();
            session.markError();
            recorder.persist(session);
            throw e;
        }
        TraceSession.end();

        return reply
                .doOnNext(session::appendAnswer)
                .doOnError(e -> session.markError())
                .doFinally(signal -> {
                    if (signal == SignalType.CANCEL) {
                        // 客户端提前断开：既没答完也没报错。单独标出来，
                        // 免得和「正常答完」「真出错了」混在一起污染统计。
                        session.markIncomplete();
                    }
                    recorder.persist(session);
                });
    }
}
