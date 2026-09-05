package com.linggong.controller;

import com.linggong.dto.JobFormDTO;
import com.linggong.dto.Result;
import com.linggong.service.IJobService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗位接口：发布 / 编辑 / 下架 / 详情 / 分类分页 / 关键词搜索。
 */
@RestController
@RequestMapping("/job")
public class JobController {

    private final IJobService jobService;

    public JobController(IJobService jobService) {
        this.jobService = jobService;
    }

    /**
     * 发布岗位（仅雇主）。
     */
    @PostMapping
    public Result publish(@Valid @RequestBody JobFormDTO form) {
        return jobService.publish(form);
    }

    /**
     * 编辑岗位（仅发布者本人）。
     */
    @PutMapping("/{id}")
    public Result update(@PathVariable("id") Long id, @Valid @RequestBody JobFormDTO form) {
        return jobService.update(id, form);
    }

    /**
     * 下架岗位（仅发布者本人）。
     */
    @PutMapping("/off/{id}")
    public Result offShelf(@PathVariable("id") Long id) {
        return jobService.offShelf(id);
    }

    /**
     * 查询岗位详情。
     */
    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id") Long id) {
        return jobService.queryById(id);
    }

    /**
     * 附近岗位搜索（按距离升序，半径单位米）。
     */
    @GetMapping("/nearby")
    public Result queryNearby(@RequestParam("categoryId") Long categoryId,
                              @RequestParam("x") Double x,
                              @RequestParam("y") Double y,
                              @RequestParam(value = "radius", defaultValue = "5000") Double radius,
                              @RequestParam(value = "page", defaultValue = "1") Integer page,
                              @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return jobService.queryNearby(categoryId, x, y, radius, page, pageSize);
    }

    /**
     * 按分类分页查询上架岗位。
     */
    @GetMapping("/category/{categoryId}")
    public Result queryByCategory(@PathVariable("categoryId") Long categoryId,
                                  @RequestParam(value = "page", defaultValue = "1") Integer page,
                                  @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return jobService.queryByCategory(categoryId, page, pageSize);
    }

    /**
     * 按关键词搜索上架岗位。
     */
    @GetMapping("/search/{keyword}")
    public Result queryByKeyword(@PathVariable("keyword") String keyword,
                                 @RequestParam(value = "page", defaultValue = "1") Integer page,
                                 @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return jobService.queryByKeyword(keyword, page, pageSize);
    }
}
