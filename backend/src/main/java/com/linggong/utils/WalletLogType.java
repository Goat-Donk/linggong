package com.linggong.utils;

/**
 * 钱包流水类型常量。
 *
 * <p>覆盖履约闭环里所有资金变动语义：
 * <ul>
 *   <li>{@link #RECHARGE} 充值：用户主动往钱包充虚拟币（正入账）；</li>
 *   <li>{@link #FREEZE} 冻结：雇主发岗时担保金从可用余额转出（负出账）；</li>
 *   <li>{@link #UNFREEZE} 解冻：结算后退还冻结剩余（正入账）；</li>
 *   <li>{@link #SALARY} 工资：结算时给打工人发工资（打工人正入账，雇主不记工资流水）；</li>
 *   <li>{@link #SERVICE_FEE} 服务费：结算时平台抽成 10%，由雇主承担（雇主负出账）。</li>
 * </ul>
 */
public final class WalletLogType {

    /** 充值 */
    public static final String RECHARGE = "充值";

    /** 冻结（发岗担保金） */
    public static final String FREEZE = "冻结";

    /** 解冻（结算后退还冻结剩余） */
    public static final String UNFREEZE = "解冻";

    /** 工资（结算发给打工人） */
    public static final String SALARY = "工资";

    /** 服务费（结算平台抽成，雇主承担） */
    public static final String SERVICE_FEE = "服务费";

    private WalletLogType() {
    }
}
