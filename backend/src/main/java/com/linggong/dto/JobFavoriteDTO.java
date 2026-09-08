package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 收藏返回对象：收藏记录 + 岗位简要信息（列表页用）。
 */
@Data
@Schema(description = "收藏返回对象（含岗位信息）")
public class JobFavoriteDTO {

    /** 收藏记录 id */
    private Long id;

    /** 岗位 id */
    private Long jobId;

    /** 岗位名称 */
    private String jobName;

    /** 工作地址 */
    private String address;

    /** 薪资（元），可空 */
    private Integer salary;

    /** 名额 */
    private Integer headcount;

    /** 岗位状态：0 上架 / 1 下架 */
    private Integer status;

    /** 收藏时间 */
    private String createTime;
}
