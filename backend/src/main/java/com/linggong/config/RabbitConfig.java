package com.linggong.config;

import com.linggong.utils.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
