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
 * <h3>为什么既要 ThreadLocal 又要闭包</h3>
 * langchain4j 的链路横跨两条线程，这两件事必须用不同手段解决，混为一谈就会丢数据：
 * <ul>
 *   <li><b>检索</b>发生在调用方线程上。这一点是从字节码里核实过的：{@code DefaultAiServices}
 *       的代理方法体里<b>同步</b>调用 {@code retrievalAugmentor.augment(...)}，
 *       再把结果塞进 {@code AiServiceTokenStreamParameters}，也就是说
 *       {@code aiAssistant.chat(...)} 一返回，检索就已经做完了。所以检索器与调用方同线程，
 *       用 {@link ThreadLocal} 就能拿到当前会话。</li>
 *   <li><b>回答分片</b>是在模型回调线程（okhttp 派发线程）上到达的，那条线程上没有本 ThreadLocal。
 *       所以调用方必须把 session 对象<b>捕获进 Flux 的闭包</b>（{@code doOnNext(session::appendAnswer)}），
 *       而不是指望回调里能 {@code current()} 到它。</li>
 * </ul>
 * 反过来说：如果哪天有人把检索改成异步（比如并行调用两路召回），{@code current()} 就会返回 null，
 * 检索数据会静默丢失。为此 {@link TracingRuleRetriever} 在拿不到会话时会打一条 WARN —— 让这件事
 * 暴露出来，而不是安静地少记一列。
 *
 * <h3>为什么结束分两步</h3>
 * {@link #end()} 只清 ThreadLocal（检索做完就该清，避免线程复用串数据），
 * {@link #finish()} 才产出待落库的实体（要等回答流结束）。
 * 之所以不在这里落库：这个类不该依赖 Mapper，落库与容错都归 {@code AiTraceRecorder}。
 */
public final class TraceSession {

    /** final_answer 列是 varchar(2048)，留点余量 */
    static final int MAX_ANSWER_CHARS = 2000;

    private static final ThreadLocal<TraceSession> CURRENT = new ThreadLocal<>();

    private final String traceId = UUID.randomUUID().toString().replace("-", "");
    private final Long userId;
    private final Integer userRole;
    private final String query;
    private final long startNanos = System.nanoTime();

    private final StringBuilder answer = new StringBuilder();
    private final List<Long> retrievedRuleIds = new ArrayList<>();

    private Long bm25Nanos;
    private boolean retrievalFailed;
    private Long firstTokenNanos;
    private String status = AiTrace.STATUS_OK;
    private boolean truncated;

    private TraceSession(Long userId, Integer userRole, String query) {
        this.userId = userId;
        this.userRole = userRole;
        this.query = query;
    }

    // ===== 生命周期 =====

    /**
     * 开始一次追踪，并把会话挂到当前线程。
     *
     * <p>调用方必须保证随后同步发起 {@code aiAssistant.chat(...)}，并在返回后立刻 {@link #end()}：
     * 会话绑定的是「当前线程这一小段同步执行」，不是整个流式回答的生命周期。
     */
    public static TraceSession begin(Long userId, Integer userRole, String query) {
        TraceSession session = new TraceSession(userId, userRole, query);
        CURRENT.set(session);
        return session;
    }

    /** 当前线程正在追踪的会话；不在追踪中返回 null。 */
    public static TraceSession current() {
        return CURRENT.get();
    }

    /** 摘掉当前线程的会话。幂等。 */
    public static void end() {
        CURRENT.remove();
    }

    // ===== 检索侧（调用方线程） =====

    /** 检索成功，记录命中的规则 id 与耗时。 */
    public void recordRetrieval(List<Long> ruleIds, long elapsedNanos) {
        this.bm25Nanos = elapsedNanos;
        if (ruleIds != null) {
            this.retrievedRuleIds.clear();
            this.retrievedRuleIds.addAll(ruleIds);
        }
    }

    /** 检索抛异常。异常本身由装饰器原样上抛，这里只留痕，让 trace 能区分「检索坏了」与「模型坏了」。 */
    public void recordRetrievalFailure(long elapsedNanos) {
        this.bm25Nanos = elapsedNanos;
        this.retrievalFailed = true;
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
        trace.setRetrievedRuleIds(AiTraceJson.ruleIds(retrievedRuleIds));
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
        // 查询改写尚未接入（规划中的 C0 之后才有 QueryTransformer）
        segments.put("queryRewriteMs", null);
        segments.put("bm25Ms", millis(bm25Nanos));
        // 重排尚未接入（规划中的 C3，且要等语料变大后才值得做）
        segments.put("rerankMs", null);
        segments.put("firstTokenMs", millis(firstTokenNanos));
        segments.put("totalMs", millis(System.nanoTime() - startNanos));
        if (retrievalFailed) {
            segments.put("retrievalFailed", true);
        }
        return segments;
    }

    private static Long millis(Long nanos) {
        return nanos == null ? null : nanos / 1_000_000;
    }
}
