package com.linggong.mq;

import cn.hutool.json.JSONUtil;
import com.linggong.dto.ApplyMessage;
import com.linggong.entity.JobApplication;
import com.linggong.mapper.JobApplicationMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.utils.MqConstants;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 报名消息消费者：监听主队列，异步把报名记录落库 + 扣 DB 名额。
 *
 * <p>关键设计：
 * <ul>
 *   <li>幂等：以报名单号（主键）判重，重复消息直接 ACK 丢弃，保证消费不产生重复记录；</li>
 *   <li>手动 ACK：落单成功 basicAck；落单异常 basicNack(requeue=false) 进死信队列，避免丢失；</li>
 *   <li>扣名额：UPDATE headcount = headcount - 1 WHERE headcount &gt; 0，防止扣成负数。</li>
 * </ul>
 */
@Slf4j
@Component
public class JobApplicationConsumer {

    private final JobApplicationMapper jobApplicationMapper;
    private final JobMapper jobMapper;

    public JobApplicationConsumer(JobApplicationMapper jobApplicationMapper, JobMapper jobMapper) {
        this.jobApplicationMapper = jobApplicationMapper;
        this.jobMapper = jobMapper;
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

            // 3. 写报名记录（主键 = 雪花单号，天然唯一）
            JobApplication application = new JobApplication();
            application.setId(msg.getOrderId());
            application.setJobId(msg.getJobId());
            application.setWorkerId(msg.getWorkerId());
            application.setStatus(0);
            jobApplicationMapper.insert(application);

            // 4. 扣 DB 名额（headcount > 0 才扣，防止负数）
            int rows = jobMapper.deductHeadcount(msg.getJobId());
            if (rows == 0) {
                log.warn("扣减名额失败（名额已为 0 或岗位不存在），jobId={}", msg.getJobId());
            }

            // 5. 落单成功，手动确认
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("报名落单失败，消息进入死信队列，body={}", new String(message.getBody(), StandardCharsets.UTF_8), e);
            // requeue=false：不重新入队，转投死信队列，避免无限重试阻塞主队列
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
