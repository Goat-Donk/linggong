package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 雇主经营汇总（我的岗位页顶部统计条）。
 *
 * <p>口径：
 * <ul>
 *   <li>totalJobs —— 累计发岗：我发布过的岗位总数（含已下架/已结算）；</li>
 *   <li>hiringJobs —— 招聘中：当前仍上架（status=0）的岗位数；</li>
 *   <li>frozenAmount —— 担保冻结中：未结算岗位仍在平台冻结的担保金合计；</li>
 *   <li>settledJobs —— 已结算岗位：已生成结算单的岗位数；</li>
 *   <li>totalServiceFee —— 累计服务费：全部结算单按 10% 抽取的服务费合计。</li>
 * </ul>
 */
@Data
@Schema(description = "雇主经营汇总")
public class EmployerSummaryDTO {

    /** 累计发岗 */
    @Schema(description = "累计发岗")
    private Long totalJobs;

    /** 招聘中（上架） */
    @Schema(description = "招聘中（上架）")
    private Long hiringJobs;

    /** 担保冻结中（元） */
    @Schema(description = "担保冻结中（元）")
    private BigDecimal frozenAmount;

    /** 已结算岗位 */
    @Schema(description = "已结算岗位")
    private Long settledJobs;

    /** 累计服务费（元） */
    @Schema(description = "累计服务费（元）")
    private BigDecimal totalServiceFee;
}
