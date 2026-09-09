package com.linggong.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.ApplyMessage;
import com.linggong.dto.EmployerApplicationDTO;
import com.linggong.dto.JobApplicationDTO;
import com.linggong.dto.Result;
import com.linggong.entity.Job;
import com.linggong.entity.JobApplication;
import com.linggong.entity.Notification;
import com.linggong.entity.User;
import com.linggong.mapper.JobApplicationMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.mapper.UserMapper;
import com.linggong.service.IJobApplicationService;
import com.linggong.service.INotificationService;
import com.linggong.utils.CacheClient;
import com.linggong.utils.MqConstants;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.RedisIdWorker;
import com.linggong.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报名服务实现。
 *
 * <p>报名流程（对齐黑马点评秒杀，Redis Stream 换成 RabbitMQ）：
 * <ol>
 *   <li>校验：岗位存在且上架、非本人发布；</li>
 *   <li>名额预热：Redis 无名额缓存时从 DB 懒加载（覆盖历史岗位）；</li>
 *   <li>Lua 原子：查名额 → 一人一单 → 扣名额 → 记标记；</li>
 *   <li>成功则生成雪花单号，发消息到 RabbitMQ，由消费者异步落单。</li>
 * </ol>
 *
 * <p>报名状态机：0 待确认 →（雇主审核）1 已录用 / 3 已取消 → 2 已完成。
 */
@Service
public class JobApplicationServiceImpl extends ServiceImpl<JobApplicationMapper, JobApplication>
        implements IJobApplicationService {

    private final JobMapper jobMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisIdWorker redisIdWorker;
    private final RabbitTemplate rabbitTemplate;
    private final DefaultRedisScript<Long> seckillScript;
    private final RedissonClient redissonClient;
    private final INotificationService notificationService;
    private final CacheClient cacheClient;

    public JobApplicationServiceImpl(JobMapper jobMapper, UserMapper userMapper,
                                     StringRedisTemplate stringRedisTemplate, RedisIdWorker redisIdWorker,
                                     RabbitTemplate rabbitTemplate, DefaultRedisScript<Long> seckillScript,
                                     RedissonClient redissonClient, INotificationService notificationService,
                                     CacheClient cacheClient) {
        this.jobMapper = jobMapper;
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisIdWorker = redisIdWorker;
        this.rabbitTemplate = rabbitTemplate;
        this.seckillScript = seckillScript;
        this.redissonClient = redissonClient;
        this.notificationService = notificationService;
        this.cacheClient = cacheClient;
    }

    @Override
    public Result apply(Long jobId) {
        // 角色边界：只有打工人（role=0）能报名应聘，雇主不能去报名别人岗位
        Integer role = UserHolder.getUser().getRole();
        if (role == null || role != 0) {
            return Result.fail("只有打工人可以报名");
        }
        Long workerId = UserHolder.getUser().getId();

        // 1. 岗位校验：存在 + 上架 + 非本人发布
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        if (job.getStatus() == null || job.getStatus() != 0) {
            return Result.fail("岗位已下架，无法报名");
        }
        if (job.getEmployerId().equals(workerId)) {
            return Result.fail("不能报名自己发布的岗位");
        }

        // 2. 名额预热：Redis 无缓存时从 DB 懒加载（覆盖 Phase 2 已发布的老岗位）
        ensureApplyStock(job);

        // 3. 执行 Lua 秒杀（原子：扣名额 + 一人一单）
        Long result = stringRedisTemplate.execute(
                seckillScript,
                Collections.emptyList(),
                String.valueOf(jobId),
                String.valueOf(workerId));
        if (result == null) {
            return Result.fail("系统繁忙，请稍后再试");
        }
        if (result == 1) {
            return Result.fail("岗位名额已满");
        }
        if (result == 2) {
            return Result.fail("请勿重复报名");
        }

        // 4. 生成报名单号 + 发消息到 RabbitMQ（异步落单）
        long orderId = redisIdWorker.nextId(RedisConstants.APPLY_ID_PREFIX);
        ApplyMessage message = new ApplyMessage(jobId, workerId, orderId);
        rabbitTemplate.convertAndSend(
                MqConstants.JOB_EXCHANGE,
                MqConstants.JOB_APPLICATION_KEY,
                JSONUtil.toJsonStr(message),
                new CorrelationData(String.valueOf(orderId)));

        // 雪花单号超出 JS 安全整数，转字符串返回，避免前端精度丢失
        return Result.ok(String.valueOf(orderId));
    }

    @Override
    public Result myApplications(Integer page, Integer pageSize) {
        Long workerId = UserHolder.getUser().getId();
        // 1. 分页查我的报名记录
        Page<JobApplication> pageResult = lambdaQuery()
                .eq(JobApplication::getWorkerId, workerId)
                .orderByDesc(JobApplication::getCreateTime)
                .page(new Page<>(page, pageSize));

        // 2. 批量查岗位，拼岗位简要信息
        List<Long> jobIds = pageResult.getRecords().stream()
                .map(JobApplication::getJobId)
                .collect(Collectors.toList());
        Map<Long, Job> jobMap = jobIds.isEmpty() ? Collections.emptyMap()
                : jobMapper.selectBatchIds(jobIds).stream()
                        .collect(Collectors.toMap(Job::getId, job -> job));

        // 3. 组装 DTO
        List<JobApplicationDTO> dtos = pageResult.getRecords().stream().map(app -> {
            JobApplicationDTO dto = BeanUtil.copyProperties(app, JobApplicationDTO.class);
            Job job = jobMap.get(app.getJobId());
            if (job != null) {
                dto.setJobName(job.getName());
                dto.setAddress(job.getAddress());
                dto.setSalary(job.getSalary());
            }
            return dto;
        }).collect(Collectors.toList());

        return Result.ok(dtos, pageResult.getTotal());
    }

    @Override
    public Result employerApplications(Integer page, Integer pageSize) {
        Long employerId = UserHolder.getUser().getId();
        // 1. 查我发布的岗位，构建 jobName 映射 + jobId 列表
        List<Job> myJobs = jobMapper.selectList(
                new LambdaQueryWrapper<Job>().eq(Job::getEmployerId, employerId));
        if (myJobs.isEmpty()) {
            return Result.ok(Collections.emptyList(), 0L);
        }
        Map<Long, Job> jobMap = myJobs.stream().collect(Collectors.toMap(Job::getId, job -> job));
        List<Long> jobIds = myJobs.stream().map(Job::getId).collect(Collectors.toList());

        // 2. 分页查这些岗位下的报名（按报名时间倒序）
        Page<JobApplication> pageResult = lambdaQuery()
                .in(JobApplication::getJobId, jobIds)
                .orderByDesc(JobApplication::getCreateTime)
                .page(new Page<>(page, pageSize));

        // 3. 批量查报名人，避免 N+1
        List<Long> workerIds = pageResult.getRecords().stream()
                .map(JobApplication::getWorkerId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = workerIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(workerIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user));

        // 4. 组装 DTO：报名 + 岗位名 + 报名人昵称头像
        List<EmployerApplicationDTO> dtos = pageResult.getRecords().stream().map(app -> {
            EmployerApplicationDTO dto = new EmployerApplicationDTO();
            dto.setId(app.getId());
            dto.setJobId(app.getJobId());
            dto.setWorkerId(app.getWorkerId());
            dto.setStatus(app.getStatus());
            dto.setCreateTime(app.getCreateTime());
            Job job = jobMap.get(app.getJobId());
            if (job != null) {
                dto.setJobName(job.getName());
            }
            User worker = userMap.get(app.getWorkerId());
            if (worker != null) {
                dto.setWorkerName(worker.getNickName());
                dto.setWorkerIcon(worker.getIcon());
            }
            return dto;
        }).collect(Collectors.toList());

        return Result.ok(dtos, pageResult.getTotal());
    }

    @Override
    @Transactional
    public Result audit(Long applicationId, boolean approve) {
        Long employerId = UserHolder.getUser().getId();
        // 分布式锁：锁定单条报名记录，把「读 → 判断 → 更新」整体包进锁内，
        // 防止并发重复审核（对标黑马点评「一人一单」的业务锁：key 设计 + 粒度 + 加锁位置）。
        RLock lock = redissonClient.getLock(RedisConstants.AUDIT_LOCK_KEY + applicationId);
        if (!lock.tryLock()) {
            return Result.fail("操作过于频繁，请稍后再试");
        }
        try {
            // 1. 报名记录存在且处于「待确认」状态
            JobApplication application = getById(applicationId);
            if (application == null) {
                return Result.fail("报名记录不存在");
            }
            if (application.getStatus() == null || application.getStatus() != 0) {
                return Result.fail("该报名已处理，不能重复审核");
            }
            // 2. 归属校验：只能审核自己发布岗位下的报名
            Job job = jobMapper.selectById(application.getJobId());
            if (job == null || !job.getEmployerId().equals(employerId)) {
                return Result.fail("只能审核自己发布岗位的报名");
            }
            // 3. 状态流转：通过 → 已录用(1)，拒绝 → 已取消(3)
            application.setStatus(approve ? 1 : 3);
            updateById(application);
            // 拒绝时释放名额：报名时已扣 DB headcount + Redis 秒杀名额，被拒须对称退回，
            // 否则名额被「被拒的报名」永久占用，岗位无法再招满。
            if (!approve) {
                releaseSlot(application);
            }
            // 4. 站内通知：把审核结果推送给工人，bizId 关联岗位便于跳转
            notificationService.notify(application.getWorkerId(),
                    approve ? Notification.TYPE_APPLY_APPROVED : Notification.TYPE_APPLY_REJECTED,
                    approve ? "报名已录用" : "报名未通过",
                    approve ? "你报名的岗位「" + job.getName() + "」已被录用，请按时到岗"
                            : "你报名的岗位「" + job.getName() + "」未通过审核",
                    application.getJobId());
            return Result.ok();
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional
    public Result cancel(Long applicationId) {
        // 角色边界：只有打工人（role=0）能撤销自己的报名
        Integer role = UserHolder.getUser().getRole();
        if (role == null || role != 0) {
            return Result.fail("只有打工人可以撤销报名");
        }
        Long workerId = UserHolder.getUser().getId();
        JobApplication application = getById(applicationId);
        if (application == null) {
            return Result.fail("报名记录不存在");
        }
        if (!application.getWorkerId().equals(workerId)) {
            return Result.fail("只能撤销自己的报名");
        }
        if (application.getStatus() == null || application.getStatus() != 0) {
            return Result.fail("该报名已处理，无法撤销");
        }
        // 1. 状态流转：0 待确认 → 3 已取消
        application.setStatus(3);
        updateById(application);
        // 2. 释放名额：DB headcount + Redis 秒杀名额 + 一人一单标记（撤销后允许再次报名）
        releaseSlot(application);
        return Result.ok();
    }

    /**
     * 释放一个名额：报名 0→3（打工人撤销 / 雇主拒绝）时调用，对称于报名时的
     * deductHeadcount + Lua 扣 stock，避免名额被已取消的报名永久占用。
     * Redis 名额是软缓存，DB headcount 才是最终真实值，二者都退回保持一致。
     */
    private void releaseSlot(JobApplication application) {
        jobMapper.restoreHeadcount(application.getJobId());
        String stockKey = RedisConstants.APPLY_STOCK_KEY + application.getJobId();
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
            stringRedisTemplate.opsForValue().increment(stockKey, 1);
        }
        stringRedisTemplate.opsForSet().remove(
                RedisConstants.APPLY_ORDER_KEY + application.getJobId(),
                String.valueOf(application.getWorkerId()));
        // 名额已恢复，删岗位详情缓存，避免「已取消报名仍占名额」的旧值误导（最长 30 分钟）
        cacheClient.delete(RedisConstants.CACHE_JOB_KEY + application.getJobId());
    }

    /**
     * 报名名额懒加载：Redis 中无该岗位名额时，从 DB 读取 headcount 预热。
     * 用 setIfAbsent 保证并发下只有一个请求真正写入，其余请求复用已有值。
     */
    private void ensureApplyStock(Job job) {
        String stockKey = RedisConstants.APPLY_STOCK_KEY + job.getId();
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey))) {
            stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(job.getHeadcount()));
        }
    }
}
