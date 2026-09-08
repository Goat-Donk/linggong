package com.linggong.controller;

import com.linggong.dto.AttendanceAuditDTO;
import com.linggong.dto.AttendanceConfirmDTO;
import com.linggong.dto.Result;
import com.linggong.service.IAttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 每日考勤接口：打工人按天申请到岗/下工，雇主按日核销/补记完工。
 */
@Tag(name = "考勤接口", description = "打工人到岗/下工申请、我的考勤；雇主按日核销、补记完工")
@RestController
@RequestMapping("/attendance")
public class AttendanceController {

    private final IAttendanceService attendanceService;

    public AttendanceController(IAttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @Operation(summary = "打工人发起今日到岗（仅打工人，当天）")
    @PostMapping("/on")
    public Result punchOn(@Parameter(description = "岗位 id") @RequestParam("jobId") Long jobId) {
        return attendanceService.punchOn(jobId);
    }

    @Operation(summary = "打工人请求今日下工（需到岗已通过，仅打工人，当天）")
    @PostMapping("/off")
    public Result punchOff(@Parameter(description = "岗位 id") @RequestParam("jobId") Long jobId) {
        return attendanceService.punchOff(jobId);
    }

    @Operation(summary = "我的考勤（岗位 + 今天可打卡状态 + 逐日记录）")
    @GetMapping("/my")
    public Result myAttendance(@Parameter(description = "岗位 id") @RequestParam("jobId") Long jobId) {
        return attendanceService.myAttendance(jobId);
    }

    @Operation(summary = "雇主考勤核销可选岗位列表（我发布的、有已录用工人）")
    @GetMapping("/jobs")
    public Result myAttendanceJobs() {
        return attendanceService.myAttendanceJobs();
    }

    @Operation(summary = "雇主查看某岗位某日全部已录用工人考勤（按日核销）")
    @GetMapping("/job")
    public Result jobAttendance(@Parameter(description = "岗位 id") @RequestParam("jobId") Long jobId,
                                @Parameter(description = "日期 yyyy-MM-dd，空则今天") @RequestParam(value = "date", required = false) String date) {
        return attendanceService.jobAttendance(jobId, date);
    }

    @Operation(summary = "雇主核销一次打卡申请（on/off 通过或驳回）")
    @PostMapping("/audit")
    public Result audit(@RequestBody AttendanceAuditDTO dto) {
        return attendanceService.audit(dto);
    }

    @Operation(summary = "雇主补记某工人今日完工（置到岗+下工通过，仅限今天）")
    @PostMapping("/confirm-off")
    public Result confirmOff(@RequestBody AttendanceConfirmDTO dto) {
        return attendanceService.confirmOff(dto);
    }
}
