package com.linggong.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.EmployerSummaryDTO;
import com.linggong.dto.JobDTO;
import com.linggong.dto.JobFormDTO;
import com.linggong.dto.JobMyDTO;
import com.linggong.dto.Result;
import com.linggong.dto.UserDTO;
import com.linggong.entity.Job;
import com.linggong.entity.JobApplication;
import com.linggong.entity.JobSettlement;
import com.linggong.mapper.JobApplicationMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.mapper.JobSettlementMapper;
import com.linggong.service.IJobService;
import com.linggong.service.IWalletService;
import com.linggong.utils.CacheClient;
import com.linggong.utils.GeoUtil;
import com.linggong.utils.JobBloomFilter;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.TaskDaysUtil;
import com.linggong.utils.UserHolder;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 *   <li>薪资=日薪（元/天）；发岗时冻结担保金 日薪×名额×任务天数，编辑按差额补冻/释放，余额不足拒绝。</li>
 * </ul>
 */
@Service
public class JobServiceImpl extends ServiceImpl<JobMapper, Job> implements IJobService {

    private final CacheClient cacheClient;
    private final JobBloomFilter jobBloomFilter;
    private final StringRedisTemplate stringRedisTemplate;
    private final IWalletService walletService;
    private final JobSettlementMapper jobSettlementMapper;
    private final JobApplicationMapper jobApplicationMapper;

    public JobServiceImpl(CacheClient cacheClient, JobBloomFilter jobBloomFilter,
                          StringRedisTemplate stringRedisTemplate, IWalletService walletService,
                          JobSettlementMapper jobSettlementMapper,
                          JobApplicationMapper jobApplicationMapper) {
        this.cacheClient = cacheClient;
        this.jobBloomFilter = jobBloomFilter;
        this.stringRedisTemplate = stringRedisTemplate;
        this.walletService = walletService;
        this.jobSettlementMapper = jobSettlementMapper;
        this.jobApplicationMapper = jobApplicationMapper;
    }

    @Override
    @Transactional
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
        // 4. 担保金预检：日薪×名额×任务天数，余额不够直接拒绝（不落库）
        BigDecimal freeze = freezeAmount(job);
        BigDecimal balance = walletService.balanceOf(user.getId());
        if (balance.compareTo(freeze) < 0) {
            return Result.fail("可用余额不足：发布需冻结担保金 ¥" + money(freeze)
                    + "（日薪 ¥" + job.getSalary() + " × " + job.getHeadcount() + " 人 × "
                    + TaskDaysUtil.taskDays(job) + " 天），当前可用 ¥" + money(balance)
                    + "，请先到「我的钱包」充值");
        }
        job.setFrozenAmount(freeze);
        save(job);
        // 5. 从余额冻结担保金；同一事务内失败则回滚，岗位不入库
        Result freezeResult = walletService.freeze(user.getId(), job.getId(), freeze,
                "发岗担保金冻结「" + job.getName() + "」");
        if (!Boolean.TRUE.equals(freezeResult.getSuccess())) {
            throw new IllegalStateException("冻结担保金失败，岗位发布已回滚");
        }
        // 6. 新岗位 id 加入布隆过滤器 + 写入 GEO（附近搜索用）+ 预热报名名额
        jobBloomFilter.add(job.getId());
        addJobGeo(job);
        preheatApplyStock(job);
        return Result.ok(job.getId());
    }

    @Override
    @Transactional
    public Result update(Long id, JobFormDTO form) {
        Job job = getById(id);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        Long userId = UserHolder.getUser().getId();
        if (!job.getEmployerId().equals(userId)) {
            return Result.fail("只能修改自己发布的岗位");
        }
        if (timeInvalid(form)) {
            return Result.fail("结束时间不能早于开始时间");
        }
        if (jobSettlementMapper.selectByJobId(id) != null) {
            return Result.fail("岗位已结算，不能编辑");
        }
        // 在途报名保护：有已录用或待确认的报名时禁止编辑，避免改薪资/名额/时间影响已报名工人
        Long hiredCount = jobApplicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getJobId, id).eq(JobApplication::getStatus, 1));
        if (hiredCount != null && hiredCount > 0) {
            return Result.fail("已有录用工人在岗，不能编辑，请通过「考勤核销」结算结束岗位");
        }
        Long pendingCount = jobApplicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getJobId, id).eq(JobApplication::getStatus, 0));
        if (pendingCount != null && pendingCount > 0) {
            return Result.fail("有待确认的报名，请先处理报名或下架岗位");
        }
        // 记录旧分类，用于从旧 GEO 集合移除（copyProperties 后 categoryId 可能已变）
        Long oldCategoryId = job.getCategoryId();
        // 担保差额：用「form 有则用 form、缺则沿用 DB 现值」的快照算新冻结额。
        // 不能直接对 copyProperties(form, job) 算：hutool 会用 null 覆盖内存里的可选字段，
        // 导致漏填的时间被误当 1 天；DB 因 MyBatis-Plus 忽略 null 才保留了旧时间，两者不一致。
        Job effective = new Job();
        effective.setSalary(form.getSalary() != null ? form.getSalary() : job.getSalary());
        effective.setHeadcount(form.getHeadcount() != null ? form.getHeadcount() : job.getHeadcount());
        effective.setStartTime(form.getStartTime() != null ? form.getStartTime() : job.getStartTime());
        effective.setEndTime(form.getEndTime() != null ? form.getEndTime() : job.getEndTime());
        BigDecimal oldFreeze = job.getFrozenAmount() == null ? BigDecimal.ZERO : job.getFrozenAmount();
        BigDecimal newFreeze = freezeAmount(effective);
        BigDecimal delta = newFreeze.subtract(oldFreeze);
        if (delta.signum() > 0) {
            BigDecimal balance = walletService.balanceOf(userId);
            if (balance.compareTo(delta) < 0) {
                return Result.fail("可用余额不足：本次调整需补冻结担保金 ¥" + money(delta)
                        + "，当前可用 ¥" + money(balance) + "，请先到「我的钱包」充值");
            }
        }
        // form 不含 id/employerId/status/createTime，copyProperties 不会覆盖这些字段；
        // 可选字段（address/description/start/end）漏填时为 null，updateById 默认不写 null，DB 保留原值
        BeanUtil.copyProperties(form, job);
        job.setFrozenAmount(newFreeze);
        updateById(job);
        // 按差额调整冻结：上调补冻、下调释放（同一事务，失败回滚整次编辑）
        if (delta.signum() > 0) {
            Result r = walletService.freeze(userId, id, delta, "编辑岗位担保金上调「" + job.getName() + "」");
            if (!Boolean.TRUE.equals(r.getSuccess())) {
                throw new IllegalStateException("补冻担保金失败，编辑已回滚");
            }
        } else if (delta.signum() < 0) {
            walletService.unfreeze(userId, id, delta.negate(), "编辑岗位担保金下调「" + job.getName() + "」，退回差额");
        }
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
    @Transactional
    public Result offShelf(Long id) {
        Job job = getById(id);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        if (!job.getEmployerId().equals(UserHolder.getUser().getId())) {
            return Result.fail("只能下架自己发布的岗位");
        }
        if (jobSettlementMapper.selectByJobId(id) != null) {
            return Result.fail("岗位已结算，无需重复下架");
        }
        // 在途报名保护：已有录用工人在岗时不能直接下架，须先结算
        Long hiredCount = jobApplicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getJobId, id).eq(JobApplication::getStatus, 1));
        if (hiredCount != null && hiredCount > 0) {
            return Result.fail("已有录用工人在岗，不能下架，请通过「考勤核销」结算结束岗位");
        }
        // 下架时顺带取消待确认的报名（0→3），避免工人卡在「待确认」
        Long pendingCount = jobApplicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getJobId, id).eq(JobApplication::getStatus, 0));
        if (pendingCount != null && pendingCount > 0) {
            JobApplication upd = new JobApplication();
            upd.setStatus(3);
            jobApplicationMapper.update(upd, new LambdaUpdateWrapper<JobApplication>()
                    .eq(JobApplication::getJobId, id)
                    .eq(JobApplication::getStatus, 0));
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

    @Override
    public Result queryList(String keyword, Long categoryId, Integer minSalary, Integer maxSalary,
                            Double x, Double y, Double maxDistance, String sort,
                            Integer page, Integer pageSize) {
        // 距离排序 / 筛选必须提供用户坐标
        boolean needDistance = "distance".equals(sort) || maxDistance != null;
        if (needDistance && (x == null || y == null)) {
            return Result.fail("按距离排序或筛选需要提供定位");
        }

        // 1. 非距离条件统一组装（关键词 / 分类 / 薪资区间 / 上架）
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<Job>()
                .eq(Job::getStatus, 0)
                .eq(categoryId != null, Job::getCategoryId, categoryId)
                .like(StringUtils.hasText(keyword), Job::getName, keyword)
                .ge(minSalary != null, Job::getSalary, minSalary)
                .le(maxSalary != null, Job::getSalary, maxSalary);

        // 2. 不涉及距离：数据库排序 + 分页（性能最好）
        if (!needDistance) {
            if ("salary".equals(sort)) {
                wrapper.orderByDesc(Job::getSalary);
            } else {
                wrapper.orderByDesc(Job::getCreateTime);
            }
            Page<Job> result = page(new Page<>(page, pageSize), wrapper);
            return Result.ok(result.getRecords(), result.getTotal());
        }

        // 3. 涉及距离：查全量候选，Java 算距离 → 过滤 → 排序 → 内存分页。
        //    距离是「按用户坐标现算」的，无法用 DB 索引直接排序；教学项目数据量小，
        //    内存计算足够。Redis GEO 已用于「按分类附近搜索」场景，这里为支持
        //    跨分类 + 关键词 + 薪资 + 距离的任意组合，用 haversine 统一处理。
        List<JobDTO> matched = new ArrayList<>();
        for (Job job : list(wrapper)) {
            if (job.getX() == null || job.getY() == null) {
                continue; // 无坐标岗位无法参与距离计算
            }
            double distance = GeoUtil.distanceMeters(x, y, job.getX(), job.getY());
            if (maxDistance != null && distance > maxDistance) {
                continue;
            }
            JobDTO dto = BeanUtil.copyProperties(job, JobDTO.class);
            dto.setDistance(distance);
            matched.add(dto);
        }
        if ("salary".equals(sort)) {
            matched.sort(Comparator.comparing(JobDTO::getSalary,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        } else if ("distance".equals(sort)) {
            matched.sort(Comparator.comparingDouble(JobDTO::getDistance));
        } else {
            matched.sort(Comparator.comparing(JobDTO::getCreateTime).reversed());
        }
        long total = matched.size();
        int from = Math.min((page - 1) * pageSize, matched.size());
        int to = Math.min(from + pageSize, matched.size());
        return Result.ok(matched.subList(from, to), total);
    }

    @Override
    public Result myJobs(Integer page, Integer pageSize) {
        Long employerId = UserHolder.getUser().getId();
        Page<Job> pageResult = lambdaQuery()
                .eq(Job::getEmployerId, employerId)
                .orderByDesc(Job::getCreateTime)
                .page(new Page<>(page, pageSize));
        List<Job> records = pageResult.getRecords();
        if (records.isEmpty()) {
            return Result.ok(Collections.emptyList(), pageResult.getTotal());
        }
        List<Long> jobIds = records.stream().map(Job::getId).collect(Collectors.toList());
        Map<Long, Long> hiredMap = countApplicationsByStatus(jobIds, 1);
        Map<Long, Long> pendingMap = countApplicationsByStatus(jobIds, 0);
        Set<Long> settledIds = jobSettlementMapper.selectList(
                        new LambdaQueryWrapper<JobSettlement>().in(JobSettlement::getJobId, jobIds))
                .stream().map(JobSettlement::getJobId).collect(Collectors.toSet());
        List<JobMyDTO> dtos = records.stream().map(job -> {
            JobMyDTO dto = BeanUtil.copyProperties(job, JobMyDTO.class);
            dto.setHiredCount(hiredMap.getOrDefault(job.getId(), 0L).intValue());
            dto.setPendingCount(pendingMap.getOrDefault(job.getId(), 0L).intValue());
            dto.setSettled(settledIds.contains(job.getId()));
            return dto;
        }).collect(Collectors.toList());
        return Result.ok(dtos, pageResult.getTotal());
    }

    @Override
    public Result employerSummary() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getRole() == null || user.getRole() != 1) {
            return Result.fail("只有雇主可以查看经营汇总");
        }
        Long employerId = user.getId();
        EmployerSummaryDTO dto = new EmployerSummaryDTO();
        // 累计发岗：全部状态（含已下架 / 已结算）
        dto.setTotalJobs(lambdaQuery().eq(Job::getEmployerId, employerId).count());
        // 招聘中：仍上架（status=0）
        dto.setHiringJobs(lambdaQuery().eq(Job::getEmployerId, employerId)
                .eq(Job::getStatus, 0).count());
        // 担保冻结中：未结算岗位的冻结款合计（结算会清零 frozen_amount）
        dto.setFrozenAmount(baseMapper.sumFrozenByEmployer(employerId));
        // 已结算岗位 + 累计服务费：都来自结算单
        dto.setSettledJobs(jobSettlementMapper.selectCount(
                new LambdaQueryWrapper<JobSettlement>()
                        .eq(JobSettlement::getEmployerId, employerId)));
        dto.setTotalServiceFee(jobSettlementMapper.sumServiceFeeByEmployer(employerId));
        return Result.ok(dto);
    }

    /** 统计指定岗位集合下某种报名状态的数量，返回 jobId → count 映射。 */
    private Map<Long, Long> countApplicationsByStatus(List<Long> jobIds, int status) {
        List<JobApplication> apps = jobApplicationMapper.selectList(
                new LambdaQueryWrapper<JobApplication>()
                        .in(JobApplication::getJobId, jobIds)
                        .eq(JobApplication::getStatus, status));
        return apps.stream().collect(Collectors.groupingBy(
                JobApplication::getJobId, Collectors.counting()));
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

    /**
     * 担保冻结金额 = 日薪 × 名额 × 任务天数（BigDecimal，精确到分）。薪资/名额异常时返回 0。
     */
    private BigDecimal freezeAmount(Job job) {
        if (job.getSalary() == null || job.getSalary() <= 0
                || job.getHeadcount() == null || job.getHeadcount() <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(job.getSalary())
                .multiply(BigDecimal.valueOf(job.getHeadcount()))
                .multiply(BigDecimal.valueOf(TaskDaysUtil.taskDays(job)));
    }

    /** 金额展示：去掉多余的 0（900.00 → 900，50.50 → 50.50）。 */
    private String money(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
