package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发布/编辑岗位入参。
 *
 * <p>employerId 由后端从登录态（UserHolder）取，不由前端传；
 * status 发布时固定为 0（上架），createTime 由数据库生成。
 */
@Data
@Schema(description = "发布/编辑岗位入参")
public class JobFormDTO {

    @Schema(description = "分类 id")
    @NotNull(message = "岗位分类不能为空")
    private Long categoryId;

    @Schema(description = "岗位名称（最长 64 字）")
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 64, message = "岗位名称不能超过 64 字")
    private String name;

    @Schema(description = "工作地址（最长 255 字）")
    @Size(max = 255, message = "地址不能超过 255 字")
    private String address;

    @Schema(description = "经度")
    @NotNull(message = "经度不能为空")
    private Double x;

    @Schema(description = "纬度")
    @NotNull(message = "纬度不能为空")
    private Double y;

    @Schema(description = "薪资（元），可空")
    @Min(value = 0, message = "薪资不能为负")
    private Integer salary;

    @Schema(description = "名额（至少 1）")
    @NotNull(message = "名额不能为空")
    @Min(value = 1, message = "名额至少为 1")
    private Integer headcount;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "岗位描述（最长 1024 字）")
    @Size(max = 1024, message = "描述不能超过 1024 字")
    private String description;
}
