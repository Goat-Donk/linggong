package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.Result;
import com.linggong.entity.JobApplication;

/**
 * 报名服务接口。
 *
 * <p>报名核心走「Lua 原子扣名额 + RabbitMQ 异步落单」，查询/审核接口在后续步骤补充。
 */
public interface IJobApplicationService extends IService<JobApplication> {

    /**
     * 报名岗位（限量秒杀逻辑）。
     *
     * @param jobId 岗位 id
     * @return 报名结果（成功时 data 为报名单号 orderId）
     */
    Result apply(Long jobId);
}
