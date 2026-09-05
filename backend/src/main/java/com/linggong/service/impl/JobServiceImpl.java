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
import com.linggong.utils.JobBloomFilter;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.UserHolder;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 岗位服务实现。
 *
 * <p>约定：
 * <ul>
 *   <li>发布仅限雇主（role=1），写操作（编辑/下架）校验岗位归属，只能操作自己发布的岗位。</li>
 *   <li>登录态由 LoginInterceptor 保证，进入写方法时 UserHolder 一定非空。</li>
 *   <li>岗位详情走「布隆过滤器 + 逻辑过期缓存」；附近搜索走 Redis GEO。</li>
 *   <li>岗位写操作需同步维护缓存与 GEO，保证一致性。</li>
 * </ul>
 */
@Service
public class JobServiceImpl extends ServiceImpl<JobMapper, Job> implements IJobService {

    private final CacheClient cacheClient;
    private final JobBloomFilter jobBloomFilter;
    private final StringRedisTemplate stringRedisTemplate;

    public JobServiceImpl(CacheClient cacheClient, JobBloomFilter jobBloomFilter,
                          StringRedisTemplate stringRedisTemplate) {
        this.cacheClient = cacheClient;
        this.jobBloomFilter = jobBloomFilter;
        this.stringRedisTemplate = stringRedisTemplate;
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
        // 4. 新岗位 id 加入布隆过滤器 + 写入 GEO（附近搜索用）+ 预热报名名额
        jobBloomFilter.add(job.getId());
        addJobGeo(job);
        preheatApplyStock(job);
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
        // 记录旧分类，用于从旧 GEO 集合移除（copyProperties 后 categoryId 可能已变）
        Long oldCategoryId = job.getCategoryId();
        // form 不含 id/employerId/status/createTime，copyProperties 不会覆盖这些字段；
        // 且 hutool 默认忽略 null，为 null 的字段保留原值（与 UserUpdateDTO 语义一致）
        BeanUtil.copyProperties(form, job);
        updateById(job);
        // 删缓存，保证详情下次查询读到最新数据（缓存一致性）
        cacheClient.delete(RedisConstants.CACHE_JOB_KEY + id);
        // 维护 GEO：先从旧分类移除，再按新坐标写入新分类（仅上架岗位）
        removeJobGeo(oldCategoryId, id);
        if (job.getStatus() == 0) {
            addJobGeo(job);
        }
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
        // 删缓存 + 从 GEO 移除（下架后不出现在附近搜索）
        cacheClient.delete(RedisConstants.CACHE_JOB_KEY + id);
        removeJobGeo(job.getCategoryId(), id);
        return Result.ok();
    }

    @Override
    public Result queryById(Long id) {
        // 1. 布隆过滤器预判：一定不存在直接返回（缓存穿透第一道防线）
        if (!jobBloomFilter.mightContain(id)) {
            return Result.fail("岗位不存在");
        }
        // 2. 逻辑过期缓存查询（热点岗位击穿：异步重建 + 返回旧数据，可用性优先）
        Job job = queryJobWithLogicalExpire(id);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        return Result.ok(BeanUtil.copyProperties(job, JobDTO.class));
    }

    @Override
    public Result queryNearby(Long categoryId, Double x, Double y, Double radius,
                              Integer page, Integer pageSize) {
        int from = (page - 1) * pageSize;
        int end = page * pageSize;
        String key = RedisConstants.GEO_JOB_KEY + categoryId;
        // 1. GEOSEARCH 查附近岗位（按距离升序，limit 取前 end 个）
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(radius),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        if (results == null || results.getContent().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        // 2. 分页截取 from~end，解析出 id + 距离
        if (content.size() <= from) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>();
        Map<Long, Double> distanceMap = new HashMap<>();
        content.stream().skip(from).forEach(geoResult -> {
            Long jobId = Long.valueOf(geoResult.getContent().getName());
            ids.add(jobId);
            Distance distance = geoResult.getDistance();
            if (distance != null) {
                distanceMap.put(jobId, distance.getValue());
            }
        });
        // 3. 按 id 查库（listByIds 不保证顺序，手动按 GEO 距离顺序重排）
        Map<Long, Job> jobMap = listByIds(ids).stream()
                .collect(Collectors.toMap(Job::getId, job -> job));
        List<JobDTO> jobDTOs = ids.stream()
                .map(jobMap::get)
                .filter(Objects::nonNull)
                .map(job -> {
                    JobDTO dto = BeanUtil.copyProperties(job, JobDTO.class);
                    dto.setDistance(distanceMap.get(job.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
        // 附近搜索无总数（GEO 不返回总数），前端按返回条数判断是否还有下一页
        return Result.ok(jobDTOs);
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
     * 逻辑过期缓存查询：缓存未命中（未预热）时兜底查库并预热。
     */
    private Job queryJobWithLogicalExpire(Long id) {
        Job job = cacheClient.queryWithLogicalExpire(
                RedisConstants.CACHE_JOB_KEY, id, Job.class,
                this::getById, RedisConstants.CACHE_JOB_TTL, TimeUnit.MINUTES);
        if (job != null) {
            return job;
        }
        Job dbJob = getById(id);
        if (dbJob == null) {
            return null;
        }
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_JOB_KEY + id, dbJob,
                RedisConstants.CACHE_JOB_TTL, TimeUnit.MINUTES);
        return dbJob;
    }

    /**
     * 把岗位坐标写入 GEO（附近搜索用），member = 岗位 id。
     */
    private void addJobGeo(Job job) {
        if (job.getX() == null || job.getY() == null) {
            return;
        }
        stringRedisTemplate.opsForGeo().add(
                RedisConstants.GEO_JOB_KEY + job.getCategoryId(),
                new Point(job.getX(), job.getY()),
                String.valueOf(job.getId()));
    }

    /**
     * 预热报名名额到 Redis（报名秒杀用），value 存岗位名额。
     */
    private void preheatApplyStock(Job job) {
        stringRedisTemplate.opsForValue().setIfAbsent(
                RedisConstants.APPLY_STOCK_KEY + job.getId(),
                String.valueOf(job.getHeadcount()));
    }

    /**
     * 从 GEO 移除岗位（下架 / 编辑时调用）。
     */
    private void removeJobGeo(Long categoryId, Long jobId) {
        stringRedisTemplate.opsForGeo().remove(
                RedisConstants.GEO_JOB_KEY + categoryId, String.valueOf(jobId));
    }

    /**
     * 判断起止时间是否非法（结束时间早于开始时间）。
     */
    private boolean timeInvalid(JobFormDTO form) {
        return form.getStartTime() != null && form.getEndTime() != null
                && form.getEndTime().isBefore(form.getStartTime());
    }
}
