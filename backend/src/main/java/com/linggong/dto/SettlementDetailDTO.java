package com.linggong.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 岗位结算视图（预览与已结算结果共用）。
 *
 * <p>未结算时返回「即将结算」的实时推算；已结算时返回结算单快照。
 */
@Data
public class SettlementDetailDTO {

    /** 岗位 id */
    private Long jobId;

    /** 岗位名称 */
    private String jobName;

    /** 日薪（元/天） */
    private Integer salary;

    /** 已担保冻结金额（元） */
    private BigDecimal frozenAmount;

    /** 已录用（待结算）打工人数 */
    private Integer hiredCount;

    /** 是否已结算 */
    private Boolean settled;

    /** 触发来源：0 手动提前结算 / 1 到期自动结算（已结算时有值） */
    private Integer triggerType;

    /** 工资总额（元） */
    private BigDecimal grossWage;

    /** 平台服务费（元，=工资×10%，从雇主可用余额另扣） */
    private BigDecimal serviceFee;

    /** 退回雇主钱包的冻结余款（元，=冻结−工资） */
    private BigDecimal refundAmount;

    /** 结算完成时间（已结算时有值） */
    private String settleTime;

    /** 逐人结算明细 */
    private List<SettlementItemDTO> items;
}
