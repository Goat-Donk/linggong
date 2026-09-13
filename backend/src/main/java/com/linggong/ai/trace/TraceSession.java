package com.linggong.ai.trace;

import com.linggong.entity.AiTrace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一次 AI 问答的追踪会话：一条 trace 记录的「草稿」，在问答过程中逐步填满。
 *
 * <h3>跨两条线程 —— 本类所有设计的由来</h3>
 * <ul>
 *   <li><b>发起调用</b>在请求线程上（注入规则手册、调 {@code aiAssistant.chat(...)}）；</li>
 *   <li><b>回答分片</b>在模型回调线程（okhttp 派发线程）上到达，那条线程上根本没有本对象。
 *       所以调用方必须把 session <b>捕获进 Flux 的闭包</b>（{@code doOnNext(session::appendAnswer)}），
 *       而不能指望回调里能拿到它。</li>
 * </ul>
 * 也正因为分片是异步到达的，{@link #appendAnswer} / {@link #markError} / {@link #markIncomplete}
 * 全部 {@code synchronized} —— 模型回调与流终结回调可能并发碰到同一个 session。
 *
 * <h3>为什么开始与落库不在一起</h3>
 * 会话在问答开始前创建，落库要等回答流结束（{@code doFinally}）。之所以不在这里落库：
 * 这个类不该依赖 Mapper，落库与容错都归 {@link AiTraceRecorder}。
 */
public final class TraceSession {

    /** final_answer 列是 varchar(2048)，留点余量 */
    static final int MAX_ANSWER_CHARS = 2000;

    private final String traceId = UUID.randomUUID().toString().replace("-", "");
    private final Long userId;
    private final Integer userRole;
    private final String query;
    private final long startNanos = System.nanoTime();

    private final StringBuilder answer = new StringBuilder();
    private final List<Long> injectedRuleIds = new ArrayList<>();

    private Long firstTokenNanos;
    private String status = AiTrace.STATUS_OK;
    private boolean truncated;

    private TraceSession(Long userId, Integer userRole, String query) {
        this.userId = userId;
        this.userRole = userRole;
        this.query = query;
    }

    /** 开始一次追踪。 */
    public static TraceSession start(Long userId, Integer userRole, String query) {
        return new TraceSession(userId, userRole, query);
    }

    // ===== 规则依据（请求线程） =====

    /**
     * 记录本次注入提示词的规则 id。
     *
     * <p>规则库只有 19 条，走的是全量注入而非检索（见
     * {@link com.linggong.ai.rule.PlatformRuleBook}），所以这里存的是<b>全集</b>。
     * 它回答的问题也随之变了：从「检索命中了哪几条」变成「这次回答手里握着哪些依据」——
     * 归因模型答错时，前者能怪检索，后者只能怪模型，这恰好是全量注入想要的结论。
     */
    public void recordInjectedRules(List<Long> ruleIds) {
        if (ruleIds == null) {
            return;
        }
        this.injectedRuleIds.clear();
        this.injectedRuleIds.addAll(ruleIds);
    }

    // ===== 回答侧（模型回调线程） =====

    /** 收到一段回答分片。 */
    public synchronized void appendAnswer(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        if (firstTokenNanos == null) {
            firstTokenNanos = System.nanoTime() - startNanos;
        }
        int room = MAX_ANSWER_CHARS - answer.length();
        if (room <= 0) {
            truncated = true;
            return;
        }
        if (chunk.length() <= room) {
            answer.append(chunk);
            return;
        }
        // 截断时避免把一个代理对（emoji 等增补平面字符）劈成两半，留下半个字符
        int cut = room;
        if (Character.isHighSurrogate(chunk.charAt(cut - 1))) {
            cut--;
        }
        answer.append(chunk, 0, cut);
        truncated = true;
    }

    /** 流内异常。 */
    public synchronized void markError() {
        this.status = AiTrace.STATUS_ERROR;
    }

    /**
     * 流既没 complete 也没 error 就结束了（最常见的是客户端提前断开）。
     * 只在状态还是 OK 时才改写 —— 真出过错就别用「没答完」盖掉真正的错因。
     */
    public synchronized void markIncomplete() {
        if (AiTrace.STATUS_OK.equals(status)) {
            this.status = AiTrace.STATUS_INCOMPLETE;
        }
    }

    // ===== 产出 =====

    /** 当前状态，供调用方判断流是怎么结束的。 */
    public synchronized String status() {
        return status;
    }

    public String traceId() {
        return traceId;
    }

    /**
     * 产出待落库的实体。
     *
     * <p>JSON 文本由 {@link AiTraceJson} 生成，这里只负责把值搬过去。
     */
    public synchronized AiTrace finish() {
        AiTrace trace = new AiTrace();
        trace.setTraceId(traceId);
        trace.setUserId(userId);
        trace.setUserRole(userRole);
        trace.setQuery(query);
        trace.setInjectedRuleIds(AiTraceJson.ruleIds(injectedRuleIds));
        trace.setLatencyBreakdown(AiTraceJson.latency(latencySegments()));
        trace.setFinalAnswer(truncated ? answer + "……（已截断）" : answer.toString());
        trace.setStatus(status);
        return trace;
    }

    /**
     * 各段耗时（毫秒）。键名固定，未启用的阶段显式为 {@code null} ——
     * 不能写成 0，否则「这一阶段还没实现」和「这一阶段快到 0 毫秒」在数据里长得一模一样。
     */
    private Map<String, Object> latencySegments() {
        Map<String, Object> segments = new LinkedHashMap<>();
        segments.put("firstTokenMs", millis(firstTokenNanos));
        segments.put("totalMs", millis(System.nanoTime() - startNanos));
        return segments;
    }

    private static Long millis(Long nanos) {
        return nanos == null ? null : nanos / 1_000_000;
    }
}
