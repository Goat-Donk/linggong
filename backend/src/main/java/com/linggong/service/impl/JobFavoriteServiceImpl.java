package com.linggong.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.JobFavoriteDTO;
import com.linggong.dto.Result;
import com.linggong.entity.Job;
import com.linggong.entity.JobFavorite;
import com.linggong.mapper.JobFavoriteMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.service.IJobFavoriteService;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 岗位收藏服务实现。
 *
 * <p>收藏关系双写（与关注 tb_follow 模式一致）：
 * <ul>
 *   <li>DB tb_job_favorite：持久化，供收藏列表分页查询；</li>
 *   <li>Redis Set（job:favorites:{userId}）：详情页快速判断是否已收藏。</li>
 * </ul>
 * 收藏列表展示「已下架」岗位（用户需要知道自己收藏的岗位下架了），不静默过滤。
 */
@Service
public class JobFavoriteServiceImpl extends ServiceImpl<JobFavoriteMapper, JobFavorite> implements IJobFavoriteService {

    private final StringRedisTemplate stringRedisTemplate;
    private final JobMapper jobMapper;

    public JobFavoriteServiceImpl(StringRedisTemplate stringRedisTemplate, JobMapper jobMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jobMapper = jobMapper;
    }

    @Override
    public Result favorite(Long jobId, Boolean isFavorite) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.JOB_FAVORITES_KEY + userId;

        if (Boolean.TRUE.equals(isFavorite)) {
            // 已收藏则幂等返回，避免重复插入触发唯一键冲突
            Boolean favored = stringRedisTemplate.opsForSet().isMember(key, jobId.toString());
            if (Boolean.TRUE.equals(favored)) {
                return Result.ok();
            }
            JobFavorite favorite = new JobFavorite();
            favorite.setUserId(userId);
            favorite.setJobId(jobId);
            save(favorite);
            stringRedisTemplate.opsForSet().add(key, jobId.toString());
        } else {
            // 取消收藏：DB 删记录 + Redis 移除（幂等）
            remove(new LambdaQueryWrapper<JobFavorite>()
                    .eq(JobFavorite::getUserId, userId)
                    .eq(JobFavorite::getJobId, jobId));
            stringRedisTemplate.opsForSet().remove(key, jobId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFavorite(Long jobId) {
        Long userId = UserHolder.getUser().getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(
                RedisConstants.JOB_FAVORITES_KEY + userId, jobId.toString());
        return Result.ok(Boolean.TRUE.equals(isMember));
    }

    @Override
    public Result myFavorites(Integer page, Integer pageSize) {
        Long userId = UserHolder.getUser().getId();
        // 1. 分页查我的收藏记录（按收藏时间倒序）
        Page<JobFavorite> result = lambdaQuery()
                .eq(JobFavorite::getUserId, userId)
                .orderByDesc(JobFavorite::getCreateTime)
                .page(new Page<>(page, pageSize));
        List<JobFavorite> records = result.getRecords();
        if (records.isEmpty()) {
            return Result.ok(Collections.emptyList(), result.getTotal());
        }
        // 2. 批量查岗位信息避免 N+1（与 myApplications 同模式）
        List<Long> jobIds = records.stream().map(JobFavorite::getJobId).collect(Collectors.toList());
        Map<Long, Job> jobMap = jobMapper.selectBatchIds(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
        List<JobFavoriteDTO> dtos = records.stream()
                .map(record -> toDTO(record, jobMap.get(record.getJobId())))
                .collect(Collectors.toList());
        return Result.ok(dtos, result.getTotal());
    }

    /**
     * 收藏记录 + 岗位 → DTO。岗位可能已被物理删除（理论上下架是逻辑删除，此处兜底不展示岗位字段）。
     */
    private JobFavoriteDTO toDTO(JobFavorite record, Job job) {
        JobFavoriteDTO dto = new JobFavoriteDTO();
        dto.setId(record.getId());
        dto.setJobId(record.getJobId());
        dto.setCreateTime(record.getCreateTime() == null ? null : record.getCreateTime().toString());
        if (job != null) {
            dto.setJobName(job.getName());
            dto.setAddress(job.getAddress());
            dto.setSalary(job.getSalary());
            dto.setHeadcount(job.getHeadcount());
            dto.setStatus(job.getStatus());
        }
        return dto;
    }
}
