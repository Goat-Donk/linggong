package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.Result;
import com.linggong.entity.Wallet;

import java.math.BigDecimal;

/**
 * 虚拟钱包服务接口。
 *
 * <p>钱包对当前登录用户自动开户（首次访问即建 0 余额账户）。
 * 金额统一用 BigDecimal（元，精确到分）；充值只影响可用余额，
 * 担保冻结/结算记账（解冻退回、工资、服务费）都走这里，流水落 tb_wallet_log。
 */
public interface IWalletService extends IService<Wallet> {

    /**
     * 查看我的钱包（无则自动开户，返回 0 余额账户）。
     */
    Result me();

    /**
     * 模拟充值：金额为正整数（元），可用余额增加并记「充值」流水。
     */
    Result recharge(Integer amount);

    /**
     * 我的钱包流水（分页，按时间倒序）。
     */
    Result logs(Integer page, Integer pageSize);

    /**
     * 查询某用户可用余额（无钱包返回 0，不自动开户）。供发岗冻结预检/结算预检。
     */
    BigDecimal balanceOf(Long userId);

    /**
     * 冻结担保金：从 userId 可用余额扣 amount 并记「冻结」流水；余额不足返回失败。
     *
     * <p>发岗冻结 / 编辑补冻走这里。bizId 传岗位 id，remark 说明用途。
     */
    Result freeze(Long userId, Long bizId, BigDecimal amount, String remark);

    /**
     * 解冻退还：给 userId 可用余额加 amount 并记「解冻」流水。
     *
     * <p>编辑岗位下调担保金 / 结算后退回冻结剩余走这里。
     */
    Result unfreeze(Long userId, Long bizId, BigDecimal amount, String remark);

    /**
     * 结算发工资：给 workerId 可用余额加 amount 并记「工资」流水（结算引擎调用）。
     */
    Result settleSalary(Long workerId, Long bizId, BigDecimal amount, String remark);

    /**
     * 结算扣服务费：从 employerId 可用余额扣 amount 并记「服务费」流水；余额不足返回失败。
     */
    Result chargeServiceFee(Long employerId, Long bizId, BigDecimal amount, String remark);
}
