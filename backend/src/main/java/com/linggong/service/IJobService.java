package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.JobFormDTO;
import com.linggong.dto.Result;
import com.linggong.entity.Job;

/**
 * 岗位服务接口。
 */
public interface IJobService extends IService<Job> {

    /**
     * 发布岗位（仅雇主），返回新岗位 id。
     */
    Result publish(JobFormDTO form);

    /**
     * 编辑岗位（仅发布者本人）。
     */
    Result update(Long id, JobFormDTO form);

    /**
     * 下架岗位（仅发布者本人，status 置 1）。
     */
    Result offShelf(Long id);

    /**
     * 查询岗位详情。
     */
    Result queryById(Long id);

    /**
     * 按分类分页查询上架岗位（按创建时间倒序）。
     */
    Result queryByCategory(Long categoryId, Integer page, Integer pageSize);

    /**
     * 按关键词搜索上架岗位（岗位名称模糊匹配）。
     */
    Result queryByKeyword(String keyword, Integer page, Integer pageSize);
}
