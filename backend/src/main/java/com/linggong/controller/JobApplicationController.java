package com.linggong.controller;

import com.linggong.dto.Result;
import com.linggong.service.IJobApplicationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报名接口：报名岗位（限量秒杀）。
 *
 * <p>查询（我的报名）/ 审核（雇主通过/拒绝）等接口在后续步骤补充。
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
}
