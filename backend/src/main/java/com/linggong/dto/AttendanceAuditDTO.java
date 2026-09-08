package com.linggong.dto;

import lombok.Data;

/**
 * 雇主核销打卡请求体：对某工人某天某次申请（到岗/下工）做通过/驳回。
 */
@Data
public class AttendanceAuditDTO {

    /** 岗位 id */
    private Long jobId;

    /** 打工人 id */
    private Long workerId;

    /** 出勤日期 yyyy-MM-dd */
    private String workDate;

    /** 核销对象：on=到岗 / off=下工 */
    private String punch;

    /** true=通过，false=驳回 */
    private Boolean pass;
}
