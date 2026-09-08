package com.linggong.controller;

import com.linggong.dto.Result;
import com.linggong.service.IJobFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗位收藏接口：收藏 / 取消 / 是否已收藏 / 我的收藏列表。
 *
 * <p>路径前缀 /job-favorite 不在 LoginInterceptor 白名单（与 /job-application 一致），全部需登录。
 */
@Tag(name = "岗位收藏接口", description = "收藏 / 取消收藏 / 是否已收藏 / 我的收藏列表")
@RestController
@RequestMapping("/job-favorite")
public class JobFavoriteController {

    private final IJobFavoriteService jobFavoriteService;

    public JobFavoriteController(IJobFavoriteService jobFavoriteService) {
        this.jobFavoriteService = jobFavoriteService;
    }

    /**
     * 收藏 / 取消收藏岗位。
     */
    @Operation(summary = "收藏 / 取消收藏岗位")
    @PutMapping("/{jobId}/{isFavorite}")
    public Result favorite(@Parameter(description = "岗位 id") @PathVariable("jobId") Long jobId,
                           @Parameter(description = "true 收藏 / false 取消") @PathVariable("isFavorite") Boolean isFavorite) {
        return jobFavoriteService.favorite(jobId, isFavorite);
    }

    /**
     * 是否已收藏该岗位。
     */
    @Operation(summary = "是否已收藏该岗位")
    @GetMapping("/or/not/{jobId}")
    public Result isFavorite(@Parameter(description = "岗位 id") @PathVariable("jobId") Long jobId) {
        return jobFavoriteService.isFavorite(jobId);
    }

    /**
     * 我的收藏分页列表（含岗位信息）。
     */
    @Operation(summary = "我的收藏分页列表")
    @GetMapping("/my")
    public Result myFavorites(@Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                              @Parameter(description = "每页条数，默认 10") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return jobFavoriteService.myFavorites(page, pageSize);
    }
}
