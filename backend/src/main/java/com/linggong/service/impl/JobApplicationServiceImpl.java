package com.linggong.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.ApplyMessage;
import com.linggong.dto.Result;
import com.linggong.entity.Job;
import com.linggong.entity.JobApplication;
import com.linggong.mapper.JobApplicationMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.service.IJobApplicationService;
import com.linggong.utils.MqConstants;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.RedisIdWorker;
import com.linggong.utils.UserHolder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

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
 */
@Service
public class JobApplicationServiceImpl extends ServiceImpl<JobApplicationMapper, JobApplication>
        implements IJobApplicationService {

    private final JobMapper jobMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisIdWorker redisIdWorker;
    private final RabbitTemplate rabbitTemplate;
    private final DefaultRedisScript<Long> seckillScript;

    public JobApplicationServiceImpl(JobMapper jobMapper, StringRedisTemplate stringRedisTemplate,
                                     RedisIdWorker redisIdWorker, RabbitTemplate rabbitTemplate,
                                     DefaultRedisScript<Long> seckillScript) {
        this.jobMapper = jobMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisIdWorker = redisIdWorker;
        this.rabbitTemplate = rabbitTemplate;
        this.seckillScript = seckillScript;
    }

    @Override
    public Result apply(Long jobId) {
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
                JSONUtil.toJsonStr(message));

        return Result.ok(orderId);
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
