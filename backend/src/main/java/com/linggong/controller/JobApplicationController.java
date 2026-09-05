package com.linggong.controller;

import com.linggong.dto.Result;
import com.linggong.service.IJobApplicationService;
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
     * @return 报名结果，成功时 data 为报名单号
     */
    @PostMapping("/{jobId}")
    public Result apply(@PathVariable("jobId") Long jobId) {
        return jobApplicationService.apply(jobId);
    }

    /**
     * 我的报名记录（分页）。
     */
    @GetMapping("/my")
    public Result myApplications(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                 @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return jobApplicationService.myApplications(page, pageSize);
    }

    /**
     * 雇主通过报名（0 待确认 → 1 已录用）。
     */
    @PutMapping("/{id}/approve")
    public Result approve(@PathVariable("id") Long id) {
        return jobApplicationService.audit(id, true);
    }

    /**
     * 雇主拒绝报名（0 待确认 → 3 已取消）。
     */
    @PutMapping("/{id}/reject")
    public Result reject(@PathVariable("id") Long id) {
        return jobApplicationService.audit(id, false);
    }
}
