package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.Result;
import com.linggong.entity.Wallet;

/**
 * 虚拟钱包服务接口。
 *
 * <p>钱包对当前登录用户自动开户（首次访问即建 0 余额账户）。
 * 充值只影响可用余额，担保冻结/工资结算在后续 Step 里通过 {@link com.linggong.utils.WalletLogType}
 * 语义往钱包流水写账，本模块只负责「开户 / 余额 / 充值 / 流水查询」。
 */
public interface IWalletService extends IService<Wallet> {

    /**
     * 查看我的钱包（无则自动开户，返回 0 余额账户）。
     */
    Result me();

    /**
     * 模拟充值：金额为正整数，可用余额增加并记「充值」流水。
     */
    Result recharge(Integer amount);

    /**
     * 我的钱包流水（分页，按时间倒序）。
     */
    Result logs(Integer page, Integer pageSize);
}
