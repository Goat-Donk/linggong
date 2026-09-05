package com.linggong.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 报名 MQ 消息体：Lua 秒杀成功后，生产者把报名信息发给 RabbitMQ，由消费者异步落单。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplyMessage {

    /** 岗位 id */
    private Long jobId;

    /** 打工人 id */
    private Long workerId;

    /** 报名单号（RedisIdWorker 雪花算法生成） */
    private Long orderId;
}
