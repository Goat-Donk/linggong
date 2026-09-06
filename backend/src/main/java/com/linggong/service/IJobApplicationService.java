package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.Result;
import com.linggong.entity.JobApplication;

/**
 * 报名服务接口。
 *
 * <p>报名核心走「Lua 原子扣名额 + RabbitMQ 异步落单」，查询/审核走状态机。
 */
public interface IJobApplicationService extends IService<JobApplication> {

    /**
     * 报名岗位（限量秒杀逻辑）。
     *
     * @param jobId 岗位 id
     * @return 报名结果（成功时 data 为报名单号 orderId）
     */
    Result apply(Long jobId);

    /**
     * 查询当前用户的报名记录（分页，含岗位简要信息）。
     */
    Result myApplications(Integer page, Integer pageSize);

    /**
     * 查询当前雇主发布岗位下的报名记录（分页，含报名人信息），用于雇主审核。
     */
    Result employerApplications(Integer page, Integer pageSize);

    /**
     * 雇主审核报名：通过 / 拒绝。
     *
     * @param applicationId 报名记录 id
     * @param approve       true 通过（0→1 已录用），false 拒绝（0→3 已取消）
     * @return 审核结果
     */
    Result audit(Long applicationId, boolean approve);
}
