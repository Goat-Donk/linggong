package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 信用分流水实体，对应表 tb_user_credit_log。
 *
 * <p>一次信用分变动一条记录：reason_type 标明原因（互评折算 / 放鸽子扣分），
 * change_amount 为 clamp 后的真实变动值，after_credit 为变动后信用分快照，
 * biz_id 关联业务 id（如岗位），remark 给人看的说明。查询按用户倒序分页。
 */
@Data
@Schema(description = "信用分流水")
@TableName("tb_user_credit_log")
public class CreditLog {

    /** 流水原因：互评折算（好评加 / 差评减） */
    public static final String TYPE_EVALUATION = "EVALUATION";
    /** 流水原因：放鸽子（单方解除已录用岗位，发起方扣分） */
    public static final String TYPE_BREAK_HIRE = "BREAK_HIRE";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 信用分变动对象 id */
    private Long userId;

    /** 变动原因：EVALUATION / BREAK_HIRE */
    private String reasonType;

    /** 实际变动分（正=加，负=减，clamp 后真实差值） */
    private Integer changeAmount;

    /** 变动后信用分（快照） */
    private Integer afterCredit;

    /** 关联业务 id（岗位 id），可为空 */
    private Long bizId;

    /** 备注说明 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
