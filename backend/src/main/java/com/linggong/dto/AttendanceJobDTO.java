package com.linggong.dto;

import lombok.Data;

/**
 * 雇主「考勤核销」岗位选择列表项：我发布的、有已录用工人的岗位。
 */
@Data
public class AttendanceJobDTO {

    /** 岗位 id */
    private Long id;

    /** 岗位名称 */
    private String name;

    /** 日薪（元/天） */
    private Integer salary;

    /** 岗位开始时间 */
    private String startTime;

    /** 岗位结束时间 */
    private String endTime;

    /** 已录用（可打卡）打工人数 */
    private Integer hiredCount;
}
