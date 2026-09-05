package com.linggong.utils;

/**
 * RabbitMQ 相关常量：交换机 / 队列 / 路由 key。
 *
 * <p>本项目用 RabbitMQ 替代黑马点评的 Redis Stream 做报名异步落单：
 * 生产者发消息到「交换机 + 路由 key」，消费者监听「队列」，落单失败的消息进「死信队列」。
 */
public final class MqConstants {

    /** 交换机（Direct 直连） */
    public static final String JOB_EXCHANGE = "job.direct";

    /** 报名主队列 */
    public static final String JOB_APPLICATION_QUEUE = "job.application";
    /** 报名死信队列（落单重试失败后进入） */
    public static final String JOB_APPLICATION_DLQ = "job.application.dlq";

    /** 报名主队列路由 key */
    public static final String JOB_APPLICATION_KEY = "job.application";
    /** 报名死信路由 key */
    public static final String JOB_APPLICATION_DLQ_KEY = "job.application.dlq";

    private MqConstants() {
    }
}
