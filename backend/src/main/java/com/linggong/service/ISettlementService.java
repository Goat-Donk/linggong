package com.linggong.service;

import com.linggong.dto.Result;

/**
 * 岗位结算服务：履约闭环的收尾资金动作。
 *
 * <p>结算 = 岗位结束：按考勤核销终态计薪发给工人、向雇主另扣 10% 服务费、
 * 退回冻结余款、报名转已完成、岗位下架并引导互评。一岗仅结算一次（结算单 job_id 唯一）。
 */
public interface ISettlementService {

    /**
     * 雇主查看某岗位结算预览（未结算）或结果（已结算）。
     */
    Result detail(Long jobId);

    /**
     * 雇主手动提前结算（任意时刻，含 0 出勤），幂等。
     */
    Result settle(Long jobId);

    /**
     * 到期自动兜底结算：扫描 end_time 已过、仍有冻结款且未结算的岗位逐岗结算。
     * 单岗失败（如余额不足付服务费）不影响其他岗，下次任务重试。
     */
    void autoSettleExpired();
}
