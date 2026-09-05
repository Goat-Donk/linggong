package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.BlogFormDTO;
import com.linggong.dto.Result;
import com.linggong.entity.Blog;

/**
 * 动态服务接口：发布动态、我的动态、点赞、关注的人动态（滚动分页）。
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
     * 查询关注的人动态（滚动分页）。
     *
     * @param lastId   上一页返回的 minTime（首次查询传 null，内部取当前时间）
     * @param offset   偏移量（上一页返回的 offset）
     * @param pageSize 每页条数
     */
    Result queryBlogOfFollow(Long lastId, Integer offset, Integer pageSize);

    /**
     * 点赞 / 取消点赞（幂等切换）。
     */
    Result like(Long blogId);
}
