package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 「我的岗位」展示对象：雇主名下岗位 + 报名/结算状态汇总。
 */
@Data
@Schema(description = "我的岗位展示对象")
public class JobMyDTO {

    /** 岗位 id */
    private Long id;

    /** 分类 id */
    private Long categoryId;

    /** 岗位名称 */
    private String name;

    /** 工作地址 */
    private String address;

    /** 日薪（元/天） */
    private Integer salary;

    /** 名额 */
    private Integer headcount;

    /** 已冻结担保金（元） */
    private BigDecimal frozenAmount;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态：0 上架 / 1 下架 */
    private Integer status;

    /** 已录用人数（报名 status=1） */
    private Integer hiredCount;

    /** 待确认人数（报名 status=0） */
    private Integer pendingCount;

    /** 是否已结算 */
    private Boolean settled;
}
