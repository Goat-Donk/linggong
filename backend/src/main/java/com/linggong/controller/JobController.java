package com.linggong.controller;

import com.linggong.dto.JobFormDTO;
import com.linggong.dto.Result;
import com.linggong.service.IJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "岗位接口", description = "发布 / 编辑 / 下架 / 详情 / 附近搜索 / 分类分页 / 关键词搜索")
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
    @Operation(summary = "发布岗位（仅雇主）")
    @PostMapping
    public Result publish(@Valid @RequestBody JobFormDTO form) {
        return jobService.publish(form);
    }

    /**
     * 编辑岗位（仅发布者本人）。
     */
    @Operation(summary = "编辑岗位（仅发布者本人）")
    @PutMapping("/{id}")
    public Result update(@Parameter(description = "岗位 id") @PathVariable("id") Long id, @Valid @RequestBody JobFormDTO form) {
        return jobService.update(id, form);
    }

    /**
     * 下架岗位（仅发布者本人）。
     */
    @Operation(summary = "下架岗位（仅发布者本人）")
    @PutMapping("/off/{id}")
    public Result offShelf(@Parameter(description = "岗位 id") @PathVariable("id") Long id) {
        return jobService.offShelf(id);
    }

    /**
     * 查询当前雇主发布的岗位（分页，含报名与结算状态汇总）。
     */
    @Operation(summary = "查询当前雇主发布的岗位（我的岗位）")
    @GetMapping("/my")
    public Result myJobs(@Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                         @Parameter(description = "每页条数，默认 10") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return jobService.myJobs(page, pageSize);
    }

    /**
     * 我的经营汇总（仅雇主）：累计发岗 / 招聘中 / 担保冻结中 / 已结算岗位 / 累计服务费。
     */
    @Operation(summary = "我的经营汇总（仅雇主）")
    @GetMapping("/my-summary")
    public Result mySummary() {
        return jobService.employerSummary();
    }

    /**
     * 查询岗位详情。
     */
    @Operation(summary = "查询岗位详情")
    @GetMapping("/{id}")
    public Result queryById(@Parameter(description = "岗位 id") @PathVariable("id") Long id) {
        return jobService.queryById(id);
    }

    /**
     * 附近岗位搜索（按距离升序，半径单位米）。
     */
    @Operation(summary = "附近岗位搜索（按距离升序，GEO）")
    @GetMapping("/nearby")
    public Result queryNearby(@Parameter(description = "分类 id") @RequestParam("categoryId") Long categoryId,
                              @Parameter(description = "经度") @RequestParam("x") Double x,
                              @Parameter(description = "纬度") @RequestParam("y") Double y,
                              @Parameter(description = "搜索半径（米），默认 5000") @RequestParam(value = "radius", defaultValue = "5000") Double radius,
                              @Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                              @Parameter(description = "每页条数，默认 10") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return jobService.queryNearby(categoryId, x, y, radius, page, pageSize);
    }

    /**
     * 按分类分页查询上架岗位。
     */
    @Operation(summary = "按分类分页查询上架岗位")
    @GetMapping("/category/{categoryId}")
    public Result queryByCategory(@Parameter(description = "分类 id") @PathVariable("categoryId") Long categoryId,
                                  @Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                                  @Parameter(description = "每页条数，默认 10") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return jobService.queryByCategory(categoryId, page, pageSize);
    }

    /**
     * 按关键词搜索上架岗位。
     */
    @Operation(summary = "按关键词搜索上架岗位")
    @GetMapping("/search/{keyword}")
    public Result queryByKeyword(@Parameter(description = "搜索关键词") @PathVariable("keyword") String keyword,
                                 @Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                                 @Parameter(description = "每页条数，默认 10") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return jobService.queryByKeyword(keyword, page, pageSize);
    }

    /**
     * 统一岗位列表查询（关键词搜索 + 分类 + 薪资/距离筛选 + 排序 + 分页）。
     */
    @Operation(summary = "统一岗位列表查询（搜索/筛选/排序）")
    @GetMapping("/list")
    public Result queryList(@Parameter(description = "关键词") @RequestParam(required = false) String keyword,
                            @Parameter(description = "分类 id（空则全部）") @RequestParam(required = false) Long categoryId,
                            @Parameter(description = "薪资下限") @RequestParam(required = false) Integer minSalary,
                            @Parameter(description = "薪资上限") @RequestParam(required = false) Integer maxSalary,
                            @Parameter(description = "用户经度") @RequestParam(required = false) Double x,
                            @Parameter(description = "用户纬度") @RequestParam(required = false) Double y,
                            @Parameter(description = "距离上限（米）") @RequestParam(required = false) Double maxDistance,
                            @Parameter(description = "排序：latest/salary/distance，默认 latest") @RequestParam(defaultValue = "latest") String sort,
                            @Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                            @Parameter(description = "每页条数，默认 10") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return jobService.queryList(keyword, categoryId, minSalary, maxSalary, x, y, maxDistance, sort, page, pageSize);
    }
}
