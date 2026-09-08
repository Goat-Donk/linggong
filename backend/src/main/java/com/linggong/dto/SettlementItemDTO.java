package com.linggong.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 结算工人明细（预览与结果共用）：某工人核销的出勤半天数与应发工资。
 */
@Data
public class SettlementItemDTO {

    /** 打工人 id */
    private Long workerId;

    /** 打工人昵称 */
    private String workerName;

    /** 打工人头像 */
    private String workerIcon;

    /** 计薪半天数：2=满勤1天、1=半天、0=无出勤 */
    private Integer halfDays;

    /** 应发工资（元，精确到分） */
    private BigDecimal wageAmount;
}
