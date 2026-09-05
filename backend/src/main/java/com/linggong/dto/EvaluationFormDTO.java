package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布互评入参。评价人（fromUserId）由登录态注入，不接受前端传参，防止伪造。
 */
@Data
@Schema(description = "发布互评入参")
public class EvaluationFormDTO {

    @Schema(description = "岗位 id")
    @NotNull(message = "岗位不能为空")
    private Long jobId;

    @Schema(description = "被评价人 id")
    @NotNull(message = "被评价人不能为空")
    private Long toUserId;

    @Schema(description = "评分 1-5")
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低 1 分")
    @Max(value = 5, message = "评分最高 5 分")
    private Integer rating;

    @Schema(description = "评价内容（可空，最长 1024 字）")
    @Size(max = 1024, message = "评价最长 1024 字")
    private String content;
}
