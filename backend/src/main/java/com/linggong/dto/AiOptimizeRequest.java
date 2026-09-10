package com.linggong.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 优化岗位描述请求。
 *
 * <p>前端把发布表单的字段原样带上；{@code description} 可空 —— 空则由 AI 根据其它字段从零生成，
 * 非空则在草稿基础上润色。时间传展示用的 "yyyy-MM-dd HH:mm" 字符串即可，优化任务不需要精确时间戳。
 */
@Data
public class AiOptimizeRequest {

    /** 岗位名称（必填） */
    @NotBlank(message = "岗位名称不能为空")
    private String name;

    /** 岗位分类名称（可选，帮助 AI 理解岗位类型） */
    private String categoryName;

    /** 日薪（元/天） */
    @NotNull(message = "日薪不能为空")
    private Integer salary;

    /** 招聘名额 */
    @NotNull(message = "招聘名额不能为空")
    private Integer headcount;

    /** 开始时间，展示用字符串 */
    private String startTime;

    /** 结束时间，展示用字符串 */
    private String endTime;

    /** 工作地址 */
    private String address;

    /** 原始草稿，可空（空则从表单字段生成） */
    private String description;
}
