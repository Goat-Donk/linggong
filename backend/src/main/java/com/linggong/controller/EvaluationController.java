package com.linggong.controller;

import com.linggong.dto.EvaluationFormDTO;
import com.linggong.dto.Result;
import com.linggong.service.IJobEvaluationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 互评接口：发布互评、查看岗位评价列表。
 */
@RestController
@RequestMapping("/evaluation")
public class EvaluationController {

    private final IJobEvaluationService evaluationService;

    public EvaluationController(IJobEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /**
     * 发布互评（雇主评工人 / 工人评雇主）。
     */
    @PostMapping
    public Result publish(@Valid @RequestBody EvaluationFormDTO form) {
        return evaluationService.publish(form);
    }

    /**
     * 查看岗位下的评价列表（分页）。
     */
    @GetMapping("/job/{jobId}")
    public Result queryByJob(@PathVariable("jobId") Long jobId,
                             @RequestParam(value = "page", defaultValue = "1") Integer page,
                             @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return evaluationService.queryByJob(jobId, page, pageSize);
    }
}
