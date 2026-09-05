package com.linggong.controller;

import com.linggong.dto.BlogFormDTO;
import com.linggong.dto.Result;
import com.linggong.service.IBlogService;
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
 * 动态接口：发布动态、我的动态、点赞。
 */
@Tag(name = "动态接口", description = "发布晒单动态、我的动态、点赞、关注流")
@RestController
@RequestMapping("/blog")
public class BlogController {

    private final IBlogService blogService;

    public BlogController(IBlogService blogService) {
        this.blogService = blogService;
    }

    /**
     * 发布晒单动态。
     */
    @Operation(summary = "发布晒单动态")
    @PostMapping
    public Result publish(@Valid @RequestBody BlogFormDTO form) {
        return blogService.publish(form);
    }

    /**
     * 我的动态（分页）。
     */
    @Operation(summary = "我的动态（分页）")
    @GetMapping("/my")
    public Result myBlogs(@Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                          @Parameter(description = "每页条数，默认 10") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return blogService.myBlogs(page, pageSize);
    }

    /**
     * 点赞 / 取消点赞（幂等切换）。
     */
    @Operation(summary = "点赞 / 取消点赞（幂等切换）")
    @PutMapping("/like/{id}")
    public Result like(@Parameter(description = "动态 id") @PathVariable("id") Long id) {
        return blogService.like(id);
    }

    /**
     * 关注的人动态（滚动分页）。
     *
     * <p>滚动分页：首次请求只传 offset=0（lastId 省略，内部取当前时间）；
     * 后续把上一页返回的 minTime 当 lastId、offset 当 offset 传回。
     */
    @Operation(summary = "关注的人动态（滚动分页）")
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@Parameter(description = "上一页返回的 minTime（首次不传）") @RequestParam(value = "lastId", required = false) Long lastId,
                                    @Parameter(description = "偏移量（上一页返回的 offset，首次为 0）") @RequestParam(value = "offset", defaultValue = "0") Integer offset,
                                    @Parameter(description = "每页条数，默认 3") @RequestParam(value = "pageSize", defaultValue = "3") Integer pageSize) {
        return blogService.queryBlogOfFollow(lastId, offset, pageSize);
    }
}
