package com.linggong.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 雇主审核报名展示对象：报名核心字段 + 岗位名 + 报名人信息（供「雇主审核」列表展示）。
 *
 * <p>与 {@link JobApplicationDTO}（打工人视角，展示岗位信息）互补，
 * 这里展示的是「谁报了我哪个岗位」。
 */
@Data
@Schema(description = "雇主审核报名展示对象")
public class EmployerApplicationDTO {

    /** 报名单号（雪花算法，超出 JS 安全整数，序列化为字符串避免精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 岗位 id */
    private Long jobId;

    /** 岗位名称 */
    private String jobName;

    /** 报名人（打工人）id */
    private Long workerId;

    /** 报名人昵称 */
    private String workerName;

    /** 报名人头像 */
    private String workerIcon;

    /** 状态：0 待确认 / 1 已录用 / 2 已完成 / 3 已取消 */
    private Integer status;

    /** 报名时间 */
    private LocalDateTime createTime;
}
