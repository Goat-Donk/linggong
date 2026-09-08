package com.linggong.dto;

import lombok.Data;

/**
 * 雇主「补记今日完工」请求体：工人已到岗但忘了申请下工，雇主代为确认完工。
 */
@Data
public class AttendanceConfirmDTO {

    /** 岗位 id */
    private Long jobId;

    /** 打工人 id */
    private Long workerId;

    /** 出勤日期 yyyy-MM-dd（默认今天，仅允许补记今天） */
    private String workDate;
}
