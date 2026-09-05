package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.Result;
import com.linggong.entity.JobCategory;

/**
 * 岗位分类服务接口。
 */
public interface IJobCategoryService extends IService<JobCategory> {

    /**
     * 查询所有岗位分类（按 sort 升序）。
     */
    Result listAll();
}
