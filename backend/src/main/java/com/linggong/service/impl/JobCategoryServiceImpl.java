package com.linggong.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.Result;
import com.linggong.entity.JobCategory;
import com.linggong.mapper.JobCategoryMapper;
import com.linggong.service.IJobCategoryService;
import org.springframework.stereotype.Service;

/**
 * 岗位分类服务实现。
 *
 * <p>分类数据量小且基本不变，这里直接查库；后续可加 List 全量缓存。
 */
@Service
public class JobCategoryServiceImpl extends ServiceImpl<JobCategoryMapper, JobCategory>
        implements IJobCategoryService {

    @Override
    public Result listAll() {
        return Result.ok(lambdaQuery().orderByAsc(JobCategory::getSort).list());
    }
}
