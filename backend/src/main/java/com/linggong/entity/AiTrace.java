package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 问答检索追踪实体，对应表 tb_ai_trace。
 *
 * <p>一次问答一行，记录「问了什么 → 检索到什么 → 各段耗时 → 答了什么」。
 * 与 {@link Notification} 那类业务表不同，本表<b>不是业务数据</b>：它只服务于
 * Bad Case 归因和「这条回答的依据是什么」面板，因此写入失败绝不允许影响问答主流程
 * （见 {@code AiTraceRecorder}）。
 *
 * <p>{@code retrievedRuleIds} 与 {@code latencyBreakdown} 在库里是 varchar，
 * 存的是 JSON 文本，由 {@code AiTraceRecorder} 用 Jackson 序列化后写入。
 */
@Data
@TableName("tb_ai_trace")
public class AiTrace {

    /** 结束状态：正常答完 */
    public static final String STATUS_OK = "OK";
    /** 结束状态：流内异常（前端拿到的是兜底话术） */
    public static final String STATUS_ERROR = "ERROR";
    /** 结束状态：流既没完成也没报错就结束了（客户端断开等） */
    public static final String STATUS_INCOMPLETE = "INCOMPLETE";

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 追踪 id，一次问答一个（32 位无横线 UUID） */
    private String traceId;

    /** 提问人 id */
    private Long userId;

    /** 提问人角色：0 打工人 / 1 雇主 */
    private Integer userRole;

    /** 用户原始问题 */
    private String query;

    /** 检索到的规则 id，JSON 数组文本，如 {@code [24,28]}；{@code []} 表示零召回 */
    private String retrievedRuleIds;

    /** 各段耗时，JSON 对象文本（毫秒）；未启用的阶段为 null */
    private String latencyBreakdown;

    /** 最终回答（超长截断） */
    private String finalAnswer;

    /** 结束状态：OK / ERROR / INCOMPLETE */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
