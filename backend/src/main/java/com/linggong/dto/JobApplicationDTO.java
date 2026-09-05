package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报名记录展示对象：报名核心字段 + 岗位简要信息（供「我的报名」列表展示）。
 */
@Data
@Schema(description = "报名记录展示对象")
public class JobApplicationDTO {

    /** 报名单号 */
    private Long id;

    /** 岗位 id */
    private Long jobId;

    /** 岗位名称 */
    private String jobName;

    /** 岗位地址 */
    private String address;

    /** 岗位薪资（元） */
    private Integer salary;

    /** 状态：0 待确认 / 1 已录用 / 2 已完成 / 3 已取消 */
    private Integer status;

    /** 报名时间 */
    private LocalDateTime createTime;
}
