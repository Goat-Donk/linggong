package com.linggong.dto;

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
public class JobFormDTO {

    /** 分类 id */
    @NotNull(message = "岗位分类不能为空")
    private Long categoryId;

    /** 岗位名称 */
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 64, message = "岗位名称不能超过 64 字")
    private String name;

    /** 工作地址 */
    @Size(max = 255, message = "地址不能超过 255 字")
    private String address;

    /** 经度 */
    @NotNull(message = "经度不能为空")
    private Double x;

    /** 纬度 */
    @NotNull(message = "纬度不能为空")
    private Double y;

    /** 薪资（元），可空 */
    @Min(value = 0, message = "薪资不能为负")
    private Integer salary;

    /** 名额 */
    @NotNull(message = "名额不能为空")
    @Min(value = 1, message = "名额至少为 1")
    private Integer headcount;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 岗位描述 */
    @Size(max = 1024, message = "描述不能超过 1024 字")
    private String description;
}
