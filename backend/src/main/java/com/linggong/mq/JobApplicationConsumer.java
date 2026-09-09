package com.linggong.mq;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linggong.dto.ApplyMessage;
import com.linggong.entity.EmployerBlacklist;
import com.linggong.entity.Job;
import com.linggong.entity.JobApplication;
import com.linggong.mapper.EmployerBlacklistMapper;
import com.linggong.mapper.JobApplicationMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.utils.CacheClient;
import com.linggong.utils.MqConstants;
import com.linggong.utils.RedisConstants;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 报名消息消费者：监听主队列，异步把报名记录落库 + 扣 DB 名额。
 *
 * <p>关键设计：
 * <ul>
 *   <li>幂等：以报名单号（主键）判重，重复消息直接 ACK 丢弃，保证消费不产生重复记录；</li>
 *   <li>黑名单兜底：HTTP 报名时已校验一次，此处再兜底「校验后 → 落单前」雇主拉黑的极小竞态，
 *       命中则静默拒绝（不落库、不扣 DB 名额），并归还 Lua 已扣的 Redis 名额后 ACK；</li>
 *   <li>手动 ACK：落单成功 basicAck；落单异常 basicNack(requeue=false) 进死信队列，避免丢失；</li>
 *   <li>扣名额：UPDATE headcount = headcount - 1 WHERE headcount &gt; 0，防止扣成负数。</li>
 * </ul>
 */
@Slf4j
@Component
public class JobApplicationConsumer {

    private final JobApplicationMapper jobApplicationMapper;
    private final JobMapper jobMapper;
    private final CacheClient cacheClient;
    private final EmployerBlacklistMapper employerBlacklistMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public JobApplicationConsumer(JobApplicationMapper jobApplicationMapper, JobMapper jobMapper,
                                  CacheClient cacheClient, EmployerBlacklistMapper employerBlacklistMapper,
                                  StringRedisTemplate stringRedisTemplate) {
        this.jobApplicationMapper = jobApplicationMapper;
        this.jobMapper = jobMapper;
        this.cacheClient = cacheClient;
        this.employerBlacklistMapper = employerBlacklistMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @RabbitListener(queues = MqConstants.JOB_APPLICATION_QUEUE)
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 1. 解析消息
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            ApplyMessage msg = JSONUtil.toBean(body, ApplyMessage.class);

            // 2. 幂等校验：报名单号已存在说明已处理过，直接 ACK 丢弃
            if (jobApplicationMapper.selectById(msg.getOrderId()) != null) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 黑名单兜底（见类注释）：命中则静默丢弃并归还 Lua 已扣名额
            Job job = jobMapper.selectById(msg.getJobId());
            if (job != null && isBlacklisted(job.getEmployerId(), msg.getWorkerId())) {
                log.info("报名落单前命中黑名单，静默拒绝并归还名额，jobId={}, workerId={}",
                        msg.getJobId(), msg.getWorkerId());
                restoreStock(msg);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 4. 写报名记录（主键 = 雪花单号，天然唯一）
            JobApplication application = new JobApplication();
            application.setId(msg.getOrderId());
            application.setJobId(msg.getJobId());
            application.setWorkerId(msg.getWorkerId());
            application.setStatus(0);
            jobApplicationMapper.insert(application);

            // 5. 扣 DB 名额（headcount > 0 才扣，防止负数）
            int rows = jobMapper.deductHeadcount(msg.getJobId());
            if (rows == 0) {
                log.warn("扣减名额失败（名额已为 0 或岗位不存在），jobId={}", msg.getJobId());
            }
            // 6. 报名已扣名额，删岗位详情缓存，避免详情页名额与 DB 不一致（逻辑过期缓存最长 30 分钟）
            cacheClient.delete(RedisConstants.CACHE_JOB_KEY + msg.getJobId());

            // 7. 落单成功，手动确认
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("报名落单失败，消息进入死信队列，body={}", new String(message.getBody(), StandardCharsets.UTF_8), e);
            // requeue=false：不重新入队，转投死信队列，避免无限重试阻塞主队列
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private boolean isBlacklisted(Long employerId, Long workerId) {
        Long count = employerBlacklistMapper.selectCount(new LambdaQueryWrapper<EmployerBlacklist>()
                .eq(EmployerBlacklist::getEmployerId, employerId)
                .eq(EmployerBlacklist::getWorkerId, workerId));
        return count != null && count > 0;
    }

    /**
     * 归还 Lua 秒杀阶段已扣的 Redis 名额 + 一人一单标记（DB headcount 尚未扣减，无需补偿），
     * 与取消报名的 {@code releaseSlot} 语义对齐，避免黑名单丢弃后名额泄漏。
     */
    private void restoreStock(ApplyMessage msg) {
        String stockKey = RedisConstants.APPLY_STOCK_KEY + msg.getJobId();
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
            stringRedisTemplate.opsForValue().increment(stockKey, 1);
        }
        stringRedisTemplate.opsForSet().remove(
                RedisConstants.APPLY_ORDER_KEY + msg.getJobId(),
                String.valueOf(msg.getWorkerId()));
        cacheClient.delete(RedisConstants.CACHE_JOB_KEY + msg.getJobId());
    }
}
