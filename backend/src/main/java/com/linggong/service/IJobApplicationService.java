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

    /**
     * 打工人撤销报名（0 待确认 → 3 已取消），释放名额。
     *
     * @param applicationId 报名记录 id
     * @return 撤销结果
     */
    Result cancel(Long applicationId);

    /**
     * 打工人放弃已录用岗位（1 已录用 → 3 已取消），释放名额回招。
     *
     * <p>仅当该工人对本岗位还没有已核销到岗（on_status=2）时才允许，
     * 否则已有做工记录应走结算按实际付薪，避免白干。
     *
     * @param applicationId 报名记录 id
     * @return 放弃结果
     */
    Result quit(Long applicationId);

    /**
     * 雇主取消对某工人的录用（1 已录用 → 3 已取消），释放名额补招。
     *
     * <p>门槛同 {@link #quit}：无已核销考勤才允许。防止对已做工的工人赖账。
     *
     * @param applicationId 报名记录 id
     * @return 取消录用结果
     */
    Result dismiss(Long applicationId);

    /**
     * 该工人是否已被该雇主拉黑（黑名单命中则报名入口被拦）。
     */
    boolean isBlacklisted(Long employerId, Long workerId);

    /**
     * 雇主拉黑某工人时调用：把该工人对其所有岗位的「待确认(0)」报名自动取消并释放名额，
     * 避免黑名单与审核矛盾。静默（不通知被打工人）。返回取消的报名条数。
     */
    int cancelPendingOfWorkerOnEmployer(Long employerId, Long workerId);
}
