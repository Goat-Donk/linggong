package com.linggong.controller;

import com.linggong.dto.Result;
import com.linggong.dto.WorkerProfileFormDTO;
import com.linggong.service.IWorkerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 求职登记接口：打工人填写/查看自己的求职登记，雇主审核报名时查看报名人主页。
 */
@Tag(name = "求职登记接口", description = "求职登记（简历）填写与查看")
@RestController
@RequestMapping("/worker-profile")
public class WorkerProfileController {

    private final IWorkerProfileService workerProfileService;

    public WorkerProfileController(IWorkerProfileService workerProfileService) {
        this.workerProfileService = workerProfileService;
    }

    /**
     * 保存我的求职登记（不存在则插入，存在则覆盖更新，仅限本人）。
     */
    @Operation(summary = "保存我的求职登记")
    @PutMapping
    public Result save(@RequestBody WorkerProfileFormDTO form) {
        return workerProfileService.saveProfile(form);
    }

    /**
     * 查看某用户的求职登记主页（自己/雇主看报名人都可用，他人手机号脱敏）。
     */
    @Operation(summary = "查看某用户求职登记主页（脱敏）")
    @GetMapping("/view/{id}")
    public Result view(@Parameter(description = "用户 id") @PathVariable("id") Long userId) {
        return workerProfileService.viewProfile(userId);
    }
}
