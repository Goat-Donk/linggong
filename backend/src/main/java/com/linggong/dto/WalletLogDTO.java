package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 钱包流水返回 DTO（前端「我的钱包」流水列表用）。
 *
 * <p>type 直接存中文（充值/冻结/解冻/工资/服务费），amount 带符号，
 * 前端据此展示「+xx / -xx」与红绿颜色即可，无需再做映射。
 */
@Data
@Schema(description = "钱包流水 DTO")
public class WalletLogDTO {

    /** 流水 id */
    private Long id;

    /** 流水类型：充值/冻结/解冻/工资/服务费 */
    private String type;

    /** 变动金额（元，正=入账，负=出账，精确到分） */
    private BigDecimal amount;

    /** 变动后余额（元，精确到分） */
    private BigDecimal balanceAfter;

    /** 关联业务 id（可为空） */
    private Long bizId;

    /** 备注 */
    private String remark;

    /** 创建时间（yyyy-MM-dd HH:mm:ss） */
    private String createTime;
}
