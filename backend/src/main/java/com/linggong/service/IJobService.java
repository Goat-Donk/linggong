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
     * 附近岗位搜索（Redis GEO，按距离升序）。附近搜索无总数，前端按返回条数判断下一页。
     *
     * @param radius 搜索半径（米）
     */
    Result queryNearby(Long categoryId, Double x, Double y, Double radius, Integer page, Integer pageSize);

    /**
     * 按分类分页查询上架岗位（按创建时间倒序）。
     */
    Result queryByCategory(Long categoryId, Integer page, Integer pageSize);

    /**
     * 按关键词搜索上架岗位（岗位名称模糊匹配）。
     */
    Result queryByKeyword(String keyword, Integer page, Integer pageSize);

    /**
     * 统一岗位列表查询：关键词搜索 + 分类 + 薪资/距离筛选 + 排序 + 分页。
     *
     * @param keyword     岗位名称关键词（可空）
     * @param categoryId  分类 id（可空，空则全部分类）
     * @param minSalary   薪资下限（可空）
     * @param maxSalary   薪资上限（可空）
     * @param x           用户经度（距离排序/筛选时必填）
     * @param y           用户纬度（距离排序/筛选时必填）
     * @param maxDistance 距离上限（米，可空）
     * @param sort        排序：latest（默认）/ salary / distance
     */
    Result queryList(String keyword, Long categoryId, Integer minSalary, Integer maxSalary,
                     Double x, Double y, Double maxDistance, String sort,
                     Integer page, Integer pageSize);

    /**
     * 查询当前雇主发布的岗位（分页），含已录用/待确认人数与结算状态。
     */
    Result myJobs(Integer page, Integer pageSize);
}
