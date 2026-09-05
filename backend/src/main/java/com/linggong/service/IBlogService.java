package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.BlogFormDTO;
import com.linggong.dto.Result;
import com.linggong.entity.Blog;

/**
 * 动态服务接口：发布动态、我的动态、点赞。
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 发布晒单动态。
     */
    Result publish(BlogFormDTO form);

    /**
     * 查询我的动态（分页）。
     */
    Result myBlogs(Integer page, Integer pageSize);

    /**
     * 点赞 / 取消点赞（幂等切换）。
     */
    Result like(Long blogId);
}
