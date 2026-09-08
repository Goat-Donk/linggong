package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.Result;
import com.linggong.entity.Attendance;

/**
 * 每日考勤服务。
 *
 * <p>履约闭环之考勤：打工人对已录用岗位按「天」申请到岗/下工，雇主按日通过/驳回，
 * 计薪依据由核销终态推导（到岗+下工都通过=1 天、仅到岗通过=0.5 天、否则 0）。
 *
 * <p>角色边界：打卡/我的考勤仅打工人（role=0）；岗位列表/按日核销/补记仅雇主（role=1）。
 */
public interface IAttendanceService extends IService<Attendance> {

    /**
     * 打工人发起「今日到岗」申请（date=今天，服务端为准；未在任务期内/未录用不可申请）。
     */
    Result punchOn(Long jobId);

    /**
     * 打工人发起「今日下工」申请（要求到岗已通过）。
     */
    Result punchOff(Long jobId);

    /**
     * 我的考勤：某岗位今日可打卡状态 + 逐日打卡记录。
     */
    Result myAttendance(Long jobId);

    /**
     * 雇主「考勤核销」可选岗位列表（我发布的、有已录用工人的岗位）。
     */
    Result myAttendanceJobs();

    /**
     * 雇主查看某岗位某日全部已录用工人考勤（按日核销页数据源）。
     *
     * @param date 日期，空则今天
     */
    Result jobAttendance(Long jobId, String date);

    /**
     * 雇主核销一次打卡申请（通过/驳回，驳回为终态）。
     */
    Result audit(com.linggong.dto.AttendanceAuditDTO dto);

    /**
     * 雇主补记某工人今日完工（置到岗+下工均通过，只能补今天）。
     */
    Result confirmOff(com.linggong.dto.AttendanceConfirmDTO dto);
}
