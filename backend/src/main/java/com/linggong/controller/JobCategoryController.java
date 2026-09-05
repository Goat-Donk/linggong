package com.linggong.controller;

import com.linggong.dto.Result;
import com.linggong.service.IJobCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗位分类接口。
 */
@Tag(name = "岗位分类接口", description = "查询所有岗位分类")
@RestController
@RequestMapping("/job-category")
public class JobCategoryController {

    private final IJobCategoryService jobCategoryService;

    public JobCategoryController(IJobCategoryService jobCategoryService) {
        this.jobCategoryService = jobCategoryService;
    }

    /**
     * 查询所有岗位分类。
     */
    @Operation(summary = "查询所有岗位分类")
    @GetMapping("/list")
    public Result list() {
        return jobCategoryService.listAll();
    }
}
