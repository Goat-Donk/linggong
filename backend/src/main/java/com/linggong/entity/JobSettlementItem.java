package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工人结算快照。出勤用半天整数表示，避免以浮点天数参与账务计算。
 */
@Data
@TableName("tb_job_settlement_item")
public class JobSettlementItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属岗位结算单 id */
    private Long settlementId;

    /** 已完成报名记录 id（雪花 id） */
    private Long applicationId;

    /** 工人 id */
    private Long workerId;

    /** 已付半天数：2=1天、1=半天 */
    private Integer paidHalfDays;

    /** 工资金额（元，精确到分） */
    private BigDecimal wageAmount;

    private LocalDateTime createTime;
}
