package com.linggong.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.JobDTO;
import com.linggong.dto.JobFormDTO;
import com.linggong.dto.Result;
import com.linggong.dto.UserDTO;
import com.linggong.entity.Job;
import com.linggong.mapper.JobMapper;
import com.linggong.service.IJobService;
import com.linggong.utils.CacheClient;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 岗位服务实现。
 *
 * <p>约定：
 * <ul>
 *   <li>发布仅限雇主（role=1），写操作（编辑/下架）校验岗位归属，只能操作自己发布的岗位。</li>
 *   <li>登录态由 LoginInterceptor 保证，进入本类方法时 UserHolder 一定非空。</li>
 *   <li>第 2 步先直接查库，岗位详情缓存留到第 3 步再套。</li>
 * </ul>
 */
@Service
public class JobServiceImpl extends ServiceImpl<JobMapper, Job> implements IJobService {

    private final CacheClient cacheClient;

    public JobServiceImpl(CacheClient cacheClient) {
        this.cacheClient = cacheClient;
    }

    @Override
    public Result publish(JobFormDTO form) {
        // 1. 权限校验：只有雇主能发布
        UserDTO user = UserHolder.getUser();
        if (user.getRole() == null || user.getRole() != 1) {
            return Result.fail("只有雇主才能发布岗位");
        }
        // 2. 时间校验
        if (timeInvalid(form)) {
            return Result.fail("结束时间不能早于开始时间");
        }
        // 3. 组装岗位：employerId 取登录态，status 固定上架
        Job job = BeanUtil.copyProperties(form, Job.class);
        job.setEmployerId(user.getId());
        job.setStatus(0);
        save(job);
        return Result.ok(job.getId());
    }

    @Override
    public Result update(Long id, JobFormDTO form) {
        Job job = getById(id);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        if (!job.getEmployerId().equals(UserHolder.getUser().getId())) {
            return Result.fail("只能修改自己发布的岗位");
        }
        if (timeInvalid(form)) {
            return Result.fail("结束时间不能早于开始时间");
        }
        // form 不含 id/employerId/status/createTime，copyProperties 不会覆盖这些字段；
        // 且 hutool 默认忽略 null，为 null 的字段保留原值（与 UserUpdateDTO 语义一致）
        BeanUtil.copyProperties(form, job);
        updateById(job);
        // 删缓存，保证详情下次查询读到最新数据（缓存一致性）
        cacheClient.delete(RedisConstants.CACHE_JOB_KEY + id);
        return Result.ok();
    }

    @Override
    public Result offShelf(Long id) {
        Job job = getById(id);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        if (!job.getEmployerId().equals(UserHolder.getUser().getId())) {
            return Result.fail("只能下架自己发布的岗位");
        }
        job.setStatus(1);
        updateById(job);
        // 删缓存，保证详情下次查询读到最新状态（缓存一致性）
        cacheClient.delete(RedisConstants.CACHE_JOB_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryById(Long id) {
        // 走缓存：穿透（空对象）+ 击穿（互斥锁）+ 雪崩（随机 TTL）由 CacheClient 统一处理
        Job job = cacheClient.queryWithMutex(
                RedisConstants.CACHE_JOB_KEY, id, Job.class,
                this::getById, RedisConstants.CACHE_JOB_TTL, TimeUnit.MINUTES);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        return Result.ok(BeanUtil.copyProperties(job, JobDTO.class));
    }

    @Override
    public Result queryByCategory(Long categoryId, Integer page, Integer pageSize) {
        Page<Job> result = lambdaQuery()
                .eq(Job::getCategoryId, categoryId)
                .eq(Job::getStatus, 0)
                .orderByDesc(Job::getCreateTime)
                .page(new Page<>(page, pageSize));
        return Result.ok(result.getRecords(), result.getTotal());
    }

    @Override
    public Result queryByKeyword(String keyword, Integer page, Integer pageSize) {
        Page<Job> result = lambdaQuery()
                .eq(Job::getStatus, 0)
                .like(Job::getName, keyword)
                .orderByDesc(Job::getCreateTime)
                .page(new Page<>(page, pageSize));
        return Result.ok(result.getRecords(), result.getTotal());
    }

    /**
     * 判断起止时间是否非法（结束时间早于开始时间）。
     */
    private boolean timeInvalid(JobFormDTO form) {
        return form.getStartTime() != null && form.getEndTime() != null
                && form.getEndTime().isBefore(form.getStartTime());
    }
}
