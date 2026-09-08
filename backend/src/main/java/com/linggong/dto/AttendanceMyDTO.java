package com.linggong.dto;

import lombok.Data;

import java.util.List;

/**
 * 打工人「我的考勤」首页视图：岗位信息 + 今天能否到岗/下工 + 逐日打卡记录。
 */
@Data
public class AttendanceMyDTO {

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

    /** 今天日期 yyyy-MM-dd */
    private String today;

    /** 今天是否在任务期内（可打卡） */
    private Boolean inPeriod;

    /** 我是否已被该岗位录用（未录用不能打卡，记录只读） */
    private Boolean hired;

    /** 今天能否发起「到岗」申请 */
    private Boolean canOn;

    /** 今天能否发起「下工」申请 */
    private Boolean canOff;

    /** 今天操作提示（不可打卡/已提交等文案） */
    private String hint;

    /** 今天的考勤记录（今天未打卡为 null） */
    private AttendanceDayDTO todayRow;

    /** 我的逐日打卡记录（按日期升序，未打卡的天无记录） */
    private List<AttendanceDayDTO> records;
}
