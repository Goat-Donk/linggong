package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位级结算单：jobId 唯一，是结算幂等和账务结果的持久化锚点。
 */
@Data
@TableName("tb_job_settlement")
public class JobSettlement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位 id（一岗仅一单） */
    private Long jobId;

    /** 雇主 id */
    private Long employerId;

    /** 触发来源：0 手动提前结算 / 1 到期自动结算 */
    private Integer triggerType;

    /** 实际发给工人的工资总额 */
    private BigDecimal grossWage;

    /** 向雇主钱包另扣的平台服务费 */
    private BigDecimal serviceFee;

    /** 退回雇主钱包的冻结余款 */
    private BigDecimal refundAmount;

    /** 结算完成时间 */
    private LocalDateTime settledAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
