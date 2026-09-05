package com.linggong.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布互评入参。评价人（fromUserId）由登录态注入，不接受前端传参，防止伪造。
 */
@Data
public class EvaluationFormDTO {

    /** 岗位 id（必填） */
    @NotNull(message = "岗位不能为空")
    private Long jobId;

    /** 被评价人 id（必填） */
    @NotNull(message = "被评价人不能为空")
    private Long toUserId;

    /** 评分 1-5（必填） */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低 1 分")
    @Max(value = 5, message = "评分最高 5 分")
    private Integer rating;

    /** 评价内容（可空） */
    @Size(max = 1024, message = "评价最长 1024 字")
    private String content;
}
