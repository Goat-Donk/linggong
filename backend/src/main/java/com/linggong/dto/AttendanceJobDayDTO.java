package com.linggong.dto;

import lombok.Data;

import java.util.List;

/**
 * 雇主「某岗位某日」核销视图：岗位信息 + 当天全部已录用工人逐人考勤状态。
 */
@Data
public class AttendanceJobDayDTO {

    /** 岗位 id */
    private Long jobId;

    /** 岗位名称 */
    private String jobName;

    /** 日薪（元/天） */
    private Integer salary;

    /** 岗位开始时间 */
    private String startTime;

    /** 岗位结束时间 */
    private String endTime;

    /** 查看的日期 yyyy-MM-dd */
    private String date;

    /** 当天是否可核销（任务期内） */
    private Boolean inPeriod;

    /** 当天该岗位全部已录用工人的考勤状态（未打卡工人为 0 状态占位） */
    private List<AttendanceDayDTO> rows;
}
