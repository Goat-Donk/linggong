package com.linggong.controller;

import com.linggong.dto.BlogFormDTO;
import com.linggong.dto.Result;
import com.linggong.service.IBlogService;
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
    @PostMapping
    public Result publish(@Valid @RequestBody BlogFormDTO form) {
        return blogService.publish(form);
    }

    /**
     * 我的动态（分页）。
     */
    @GetMapping("/my")
    public Result myBlogs(@RequestParam(value = "page", defaultValue = "1") Integer page,
                          @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return blogService.myBlogs(page, pageSize);
    }

    /**
     * 点赞 / 取消点赞（幂等切换）。
     */
    @PutMapping("/like/{id}")
    public Result like(@PathVariable("id") Long id) {
        return blogService.like(id);
    }
}
