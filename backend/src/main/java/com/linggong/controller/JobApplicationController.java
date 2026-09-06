package com.linggong.controller;

import com.linggong.dto.Result;
import com.linggong.service.IJobApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报名接口：报名岗位（限量秒杀）、我的报名记录、雇主审核。
 */
@Tag(name = "报名接口", description = "报名岗位（限量秒杀）、我的报名记录、雇主审核")
@RestController
@RequestMapping("/job-application")
public class JobApplicationController {

    private final IJobApplicationService jobApplicationService;

    public JobApplicationController(IJobApplicationService jobApplicationService) {
        this.jobApplicationService = jobApplicationService;
    }

    /**
     * 报名岗位（仅登录用户，走 Lua 秒杀 + MQ 异步落单）。
     *
     * @param jobId 岗位 id
     * @return 报名结果，成功时 data 为报名单号（字符串，雪花 ID 避免 JS 精度丢失）
     */
    @Operation(summary = "报名岗位（Lua 秒杀 + MQ 异步落单）")
    @PostMapping("/{jobId}")
    public Result apply(@Parameter(description = "岗位 id") @PathVariable("jobId") Long jobId) {
        return jobApplicationService.apply(jobId);
    }

    /**
     * 我的报名记录（分页）。
     */
    @Operation(summary = "我的报名记录（分页）")
    @GetMapping("/my")
    public Result myApplications(@Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                                 @Parameter(description = "每页条数，默认 10") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return jobApplicationService.myApplications(page, pageSize);
    }

    /**
     * 雇主视角报名列表（我发布岗位下的报名，用于审核）。
     */
    @Operation(summary = "雇主视角报名列表（用于审核）")
    @GetMapping("/employer")
    public Result employerApplications(@Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                                       @Parameter(description = "每页条数，默认 10") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return jobApplicationService.employerApplications(page, pageSize);
    }

    /**
     * 雇主通过报名（0 待确认 → 1 已录用）。
     */
    @Operation(summary = "雇主通过报名（0 待确认 → 1 已录用）")
    @PutMapping("/{id}/approve")
    public Result approve(@Parameter(description = "报名单 id") @PathVariable("id") Long id) {
        return jobApplicationService.audit(id, true);
    }

    /**
     * 雇主拒绝报名（0 待确认 → 3 已取消）。
     */
    @Operation(summary = "雇主拒绝报名（0 待确认 → 3 已取消）")
    @PutMapping("/{id}/reject")
    public Result reject(@Parameter(description = "报名单 id") @PathVariable("id") Long id) {
        return jobApplicationService.audit(id, false);
    }
}
