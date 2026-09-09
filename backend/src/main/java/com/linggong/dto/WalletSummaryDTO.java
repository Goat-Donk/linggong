package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 打工人收入汇总（钱包页顶部统计卡）。
 *
 * <p>金额全部来自钱包流水（tb_wallet_log），口径：
 * <ul>
 *   <li>balance —— 当前可用余额（钱包实时余额）；</li>
 *   <li>totalIncome —— 累计到账工资（全部「工资」流水之和）；</li>
 *   <li>totalWithdraw —— 累计提现（全部「提现」流水绝对值之和）；</li>
 *   <li>monthIncome —— 本月到账工资（本月起的「工资」流水之和）。</li>
 * </ul>
 */
@Data
@Schema(description = "打工人收入汇总")
public class WalletSummaryDTO {

    /** 当前可用余额（元） */
    @Schema(description = "当前可用余额（元）")
    private BigDecimal balance;

    /** 累计到账工资（元） */
    @Schema(description = "累计到账工资（元）")
    private BigDecimal totalIncome;

    /** 累计提现（元，正数） */
    @Schema(description = "累计提现（元，正数）")
    private BigDecimal totalWithdraw;

    /** 本月到账工资（元） */
    @Schema(description = "本月到账工资（元）")
    private BigDecimal monthIncome;
}
