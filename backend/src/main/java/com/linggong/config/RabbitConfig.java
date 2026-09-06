package com.linggong.config;

import com.linggong.utils.MqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * RabbitMQ 组件声明：交换机 + 报名主队列 + 死信队列。
 *
 * <p>拓扑结构：
 * <pre>
 * publisher ──(routing key = job.application)──▶ 交换机 job.direct ──▶ 主队列 job.application
 * 主队列消息被拒绝(requeue=false)时，自动转投到死信交换机（复用 job.direct）
 * ──(routing key = job.application.dlq)──▶ 死信队列 job.application.dlq
 * </pre>
 *
 * <p>死信场景：消费者写表异常重试失败后 basicNack(requeue=false)，消息进死信队列，
 * 后续可人工/定时任务捞取补偿，避免消息无限重试或直接丢失。
 */
@Slf4j
@Configuration
public class RabbitConfig {

    /** 交换机 */
    @Bean
    public DirectExchange jobExchange() {
        return new DirectExchange(MqConstants.JOB_EXCHANGE);
    }

    /** 报名主队列：声明死信交换机 + 死信路由 key */
    @Bean
    public Queue jobApplicationQueue() {
        return QueueBuilder.durable(MqConstants.JOB_APPLICATION_QUEUE)
                .deadLetterExchange(MqConstants.JOB_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.JOB_APPLICATION_DLQ_KEY)
                .build();
    }

    /** 报名死信队列 */
    @Bean
    public Queue jobApplicationDlq() {
        return QueueBuilder.durable(MqConstants.JOB_APPLICATION_DLQ).build();
    }

    /** 主队列绑定交换机 */
    @Bean
    public Binding jobApplicationBinding() {
        return BindingBuilder.bind(jobApplicationQueue())
                .to(jobExchange())
                .with(MqConstants.JOB_APPLICATION_KEY);
    }

    /** 死信队列绑定交换机（与主队列共用同一个 Direct 交换机） */
    @Bean
    public Binding jobApplicationDlqBinding() {
        return BindingBuilder.bind(jobApplicationDlq())
                .to(jobExchange())
                .with(MqConstants.JOB_APPLICATION_DLQ_KEY);
    }

    /**
     * RabbitTemplate：配置发布确认（confirm）+ 不可达路由退回（return）。
     *
     * <p>把报名消息投递从 fire-and-forget 升级为「可确认」：
     * <ul>
     *   <li>confirm 回调：broker 是否确认收到消息，失败时记录 orderId，避免「静默丢消息」；</li>
     *   <li>return 回调：消息路由不到任何队列（路由 key 写错等）时触发；</li>
     *   <li>mandatory=true：路由失败时把消息退回生产者（触发 return），否则 broker 直接丢弃。</li>
     * </ul>
     * 需配合 application.yml 的 publisher-confirm-type: correlated + publisher-returns: true。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("报名消息投递失败（broker 未确认），orderId={}, cause={}",
                        correlationData == null ? null : correlationData.getId(), cause);
                // 生产环境在此接入补偿：重发 / 告警 / 死信捞取
            }
        });
        template.setReturnsCallback(returned ->
                log.error("报名消息未路由到队列，replyCode={}, replyText={}, message={}",
                        returned.getReplyCode(), returned.getReplyText(),
                        new String(returned.getMessage().getBody(), StandardCharsets.UTF_8)));
        return template;
    }
}
