package com.linggong.mq;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linggong.dto.ApplyMessage;
import com.linggong.entity.EmployerBlacklist;
import com.linggong.entity.Job;
import com.linggong.entity.JobApplication;
import com.linggong.entity.Notification;
import com.linggong.mapper.EmployerBlacklistMapper;
import com.linggong.mapper.JobApplicationMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.service.INotificationService;
import com.linggong.utils.CacheClient;
import com.linggong.utils.MqConstants;
import com.linggong.utils.RedisConstants;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
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
 *   <li>一人一单 DB 兜底：表唯一键 uk_job_worker_active 保证「同岗同工人仅一条进行中报名」，
 *       即便 Redis apply:order 漂移导致 Lua 放行，重复落单也会在此撞唯一键被丢弃（补回 Redis 名额）；</li>
 *   <li>黑名单兜底：HTTP 报名时已校验一次，此处再兜底「校验后 → 落单前」雇主拉黑的极小竞态，
 *       命中则不落库、不扣 DB 名额，并归还 Lua 已扣的 Redis 名额；</li>
 *   <li>丢弃必通知：上面两条丢弃分支都会给工人补一条脱敏站内通知（见 {@link #notifyApplyFailed}），
 *       避免「前端提示报名成功、我的报名里却查不到」的体验断层；</li>
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
    private final INotificationService notificationService;

    public JobApplicationConsumer(JobApplicationMapper jobApplicationMapper, JobMapper jobMapper,
                                  CacheClient cacheClient, EmployerBlacklistMapper employerBlacklistMapper,
                                  StringRedisTemplate stringRedisTemplate,
                                  INotificationService notificationService) {
        this.jobApplicationMapper = jobApplicationMapper;
        this.jobMapper = jobMapper;
        this.cacheClient = cacheClient;
        this.employerBlacklistMapper = employerBlacklistMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.notificationService = notificationService;
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
                notifyApplyFailed(msg);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 4. 写报名记录（主键 = 雪花单号，天然唯一）
            JobApplication application = new JobApplication();
            application.setId(msg.getOrderId());
            application.setJobId(msg.getJobId());
            application.setWorkerId(msg.getWorkerId());
            application.setStatus(0);
            try {
                jobApplicationMapper.insert(application);
            } catch (DuplicateKeyException e) {
                // 一人一单 DB 兜底（uk_job_worker_active：同岗同工人仅一条进行中报名）：
                // 走到这说明 Redis apply:order 与 DB 漂移、Lua 放行了本不该放行的重复报名。
                // 处理：补回 Lua 多扣的 Redis 名额；把该工人补回 apply:order（DB 确实已有其
                // 进行中报名，成员应存在，使后续重复报名在 Lua return2 层被拦）；不落库、ACK。
                log.info("报名落单撞一人一单 DB 唯一键（同岗同工人已有进行中报名），静默丢弃并归还名额，jobId={}, workerId={}",
                        msg.getJobId(), msg.getWorkerId());
                restoreStockForDuplicate(msg);
                notifyApplyFailed(msg);
                channel.basicAck(deliveryTag, false);
                return;
            }

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

    /**
     * 一人一单 DB 兜底归还：与 {@link #restoreStock} 的差异是**不移除** apply:order 成员，
     * 反而要**补回**（SADD 幂等）——因为撞唯一键说明该工人 DB 里确实已有进行中报名，
     * 集合成员应存在，补回后后续重复报名直接由 Lua return2 拦截，Redis 状态回归与 DB 一致。
     */
    private void restoreStockForDuplicate(ApplyMessage msg) {
        String stockKey = RedisConstants.APPLY_STOCK_KEY + msg.getJobId();
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
            stringRedisTemplate.opsForValue().increment(stockKey, 1);
        }
        stringRedisTemplate.opsForSet().add(
                RedisConstants.APPLY_ORDER_KEY + msg.getJobId(),
                String.valueOf(msg.getWorkerId()));
        cacheClient.delete(RedisConstants.CACHE_JOB_KEY + msg.getJobId());
    }

    /**
     * 报名在异步落单阶段被静默丢弃时，给工人补一条站内通知。
     *
     * <p><b>为什么必须发</b>：HTTP 报名在 Lua 扣减成功后就把雪花单号返回给了用户（前端提示「报名成功」），
     * 而记录是在这里才落库的。上面两条兜底分支把消息丢弃后，用户去「我的报名」查不到任何记录，
     * 中间没有任何提示——这是比「报名失败」严重得多的体验断层（用户以为报上了，实际没有，可能错过岗位）。
     *
     * <p><b>为什么文案脱敏</b>：不区分「被雇主拉黑」与「重复报名撞唯一键」，统一说不成功。
     * 黑名单是雇主侧的治理手段，直白告知会让工人知道被谁拉黑、诱发双方对抗；
     * 且工人知道具体拦截机制也没有可操作的下一步，提示「选择其他岗位」才是有效引导。
     *
     * <p><b>为什么 try-catch</b>：通知是「丢弃」这个结论的附带补偿，不是主流程。
     * 通知落库失败若往外抛，消息会被 basicNack 转投死信队列——但消息本身的处理（丢弃 + 归还名额）
     * 已经执行完了，进死信只会误导后续人工排查。故失败仅告警，不影响 ACK。
     */
    private void notifyApplyFailed(ApplyMessage msg) {
        try {
            notificationService.notify(msg.getWorkerId(), Notification.TYPE_APPLY_FAILED,
                    "报名未成功", "很抱歉，报名未成功，请选择其他岗位", msg.getJobId());
        } catch (Exception e) {
            log.warn("报名未成功通知发送失败，workerId={}, jobId={}", msg.getWorkerId(), msg.getJobId(), e);
        }
    }
}
