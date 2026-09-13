package com.linggong.ai.trace;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * trace 里两个 JSON 文本列的序列化。
 *
 * <p>所在位置很敏感：它被 {@link TraceSession#finish()} 调用，而 finish() 跑在流结束的回调里 ——
 * <b>在这里抛异常会直接打断用户的回答</b>。所以两个方法都保证不抛：
 * 序列化失败就退化成空数组 / 空对象，宁可少记一列数据，也不能让埋点把主流程弄挂。
 * 这与「埋点失败仅告警」是同一条原则，只是这条路径上没有日志可打，只能靠退化值兜底。
 */
final class AiTraceJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiTraceJson() {
    }

    /** 规则 id 列表 → JSON 数组文本；空列表序列化成 {@code []}（零召回，是有意义的值）。 */
    static String ruleIds(List<Long> ids) {
        try {
            return MAPPER.writeValueAsString(ids == null ? List.of() : ids);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 各段耗时 → JSON 对象文本。保留 null 值，使「阶段未启用」在数据里可辨认。 */
    static String latency(Map<String, Object> segments) {
        try {
            return MAPPER.writeValueAsString(segments == null ? Map.of() : segments);
        } catch (Exception e) {
            return "{}";
        }
    }
}
