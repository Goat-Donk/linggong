package com.linggong.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.Result;
import com.linggong.dto.WalletLogDTO;
import com.linggong.entity.Wallet;
import com.linggong.entity.WalletLog;
import com.linggong.mapper.WalletLogMapper;
import com.linggong.mapper.WalletMapper;
import com.linggong.service.IWalletService;
import com.linggong.utils.UserHolder;
import com.linggong.utils.WalletLogType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 虚拟钱包服务实现。
 *
 * <p>金额口径：全部 BigDecimal（元，两位小数）。余额变动用 SQL 原子加减
 * （decimal 列上的 + / - 不产生超出两位的精度），变动后回读落流水 balance_after。
 */
@Service
public class WalletServiceImpl extends ServiceImpl<WalletMapper, Wallet> implements IWalletService {

    /** 单次充值上限（元），防手滑大额 */
    private static final BigDecimal MAX_RECHARGE = new BigDecimal("1000000");

    /** 单次提现上限（元），与充值对称 */
    private static final BigDecimal MAX_WITHDRAW = new BigDecimal("1000000");

    private final WalletLogMapper walletLogMapper;

    public WalletServiceImpl(WalletLogMapper walletLogMapper) {
        this.walletLogMapper = walletLogMapper;
    }

    @Override
    public Result me() {
        return Result.ok(ensureWallet(UserHolder.getUser().getId()));
    }

    @Override
    @Transactional
    public Result recharge(Integer amount) {
        if (amount == null || amount <= 0) {
            return Result.fail("充值金额需大于 0");
        }
        BigDecimal value = BigDecimal.valueOf(amount);
        if (value.compareTo(MAX_RECHARGE) > 0) {
            return Result.fail("单次充值不能超过 " + MAX_RECHARGE.stripTrailingZeros().toPlainString() + " 元");
        }
        Long userId = UserHolder.getUser().getId();
        Wallet wallet = ensureWallet(userId);
        // 余额用 SQL 原子自增，避免「读-改-写」并发丢更新
        lambdaUpdate()
                .setSql("balance = balance + " + value.toPlainString())
                .eq(Wallet::getId, wallet.getId())
                .update();
        BigDecimal after = getById(wallet.getId()).getBalance();
        recordLog(userId, WalletLogType.RECHARGE, value, after, null, "模拟充值入账");
        Wallet view = new Wallet();
        view.setId(wallet.getId());
        view.setUserId(userId);
        view.setBalance(after);
        return Result.ok(view);
    }

    @Override
    @Transactional
    public Result withdraw(Integer amount) {
        if (amount == null || amount <= 0) {
            return Result.fail("提现金额需大于 0");
        }
        BigDecimal value = BigDecimal.valueOf(amount);
        if (value.compareTo(MAX_WITHDRAW) > 0) {
            return Result.fail("单次提现不能超过 " + MAX_WITHDRAW.stripTrailingZeros().toPlainString() + " 元");
        }
        Long userId = UserHolder.getUser().getId();
        Wallet wallet = ensureWallet(userId);
        if (wallet.getBalance().compareTo(value) < 0) {
            return Result.fail("可用余额不足，无法提现 ¥" + money(value)
                    + "（当前可用 ¥" + money(wallet.getBalance()) + "）");
        }
        // 余额用 SQL 原子自减，避免「读-改-写」并发丢更新
        lambdaUpdate()
                .setSql("balance = balance - " + value.toPlainString())
                .eq(Wallet::getId, wallet.getId())
                .update();
        BigDecimal after = getById(wallet.getId()).getBalance();
        recordLog(userId, WalletLogType.WITHDRAW, value.negate(), after, null, "模拟提现到账");
        Wallet view = new Wallet();
        view.setId(wallet.getId());
        view.setUserId(userId);
        view.setBalance(after);
        return Result.ok(view);
    }

    @Override
    public Result logs(Integer page, Integer pageSize) {
        Long userId = UserHolder.getUser().getId();
        Page<WalletLog> result = walletLogMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<WalletLog>()
                        .eq(WalletLog::getUserId, userId)
                        .orderByDesc(WalletLog::getCreateTime, WalletLog::getId));
        List<WalletLog> records = result.getRecords();
        if (records.isEmpty()) {
            return Result.ok(Collections.emptyList(), result.getTotal());
        }
        List<WalletLogDTO> dtos = records.stream().map(this::toDTO).collect(Collectors.toList());
        return Result.ok(dtos, result.getTotal());
    }

    @Override
    public BigDecimal balanceOf(Long userId) {
        Wallet wallet = lambdaQuery().eq(Wallet::getUserId, userId).one();
        return wallet == null || wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance();
    }

    @Override
    @Transactional
    public Result freeze(Long userId, Long bizId, BigDecimal amount, String remark) {
        Result check = positive(amount);
        if (check != null) {
            return check;
        }
        Wallet wallet = ensureWallet(userId);
        if (wallet.getBalance().compareTo(amount) < 0) {
            return Result.fail("可用余额不足，无法冻结 ¥" + money(amount)
                    + "（当前可用 ¥" + money(wallet.getBalance()) + "，请先到「我的钱包」充值）");
        }
        lambdaUpdate()
                .setSql("balance = balance - " + amount.toPlainString())
                .eq(Wallet::getId, wallet.getId())
                .update();
        BigDecimal after = getById(wallet.getId()).getBalance();
        recordLog(userId, WalletLogType.FREEZE, amount.negate(), after, bizId, remark);
        return Result.ok(after);
    }

    @Override
    @Transactional
    public Result unfreeze(Long userId, Long bizId, BigDecimal amount, String remark) {
        Result check = positive(amount);
        if (check != null) {
            return check;
        }
        Wallet wallet = ensureWallet(userId);
        lambdaUpdate()
                .setSql("balance = balance + " + amount.toPlainString())
                .eq(Wallet::getId, wallet.getId())
                .update();
        BigDecimal after = getById(wallet.getId()).getBalance();
        recordLog(userId, WalletLogType.UNFREEZE, amount, after, bizId, remark);
        return Result.ok(after);
    }

    @Override
    @Transactional
    public Result settleSalary(Long workerId, Long bizId, BigDecimal amount, String remark) {
        Result check = positive(amount);
        if (check != null) {
            return check;
        }
        Wallet wallet = ensureWallet(workerId);
        lambdaUpdate()
                .setSql("balance = balance + " + amount.toPlainString())
                .eq(Wallet::getId, wallet.getId())
                .update();
        BigDecimal after = getById(wallet.getId()).getBalance();
        recordLog(workerId, WalletLogType.SALARY, amount, after, bizId, remark);
        return Result.ok(after);
    }

    @Override
    @Transactional
    public Result chargeServiceFee(Long employerId, Long bizId, BigDecimal amount, String remark) {
        Result check = positive(amount);
        if (check != null) {
            return check;
        }
        Wallet wallet = ensureWallet(employerId);
        if (wallet.getBalance().compareTo(amount) < 0) {
            return Result.fail("可用余额不足以支付服务费 ¥" + money(amount)
                    + "（当前可用 ¥" + money(wallet.getBalance()) + "，请先到「我的钱包」充值）");
        }
        lambdaUpdate()
                .setSql("balance = balance - " + amount.toPlainString())
                .eq(Wallet::getId, wallet.getId())
                .update();
        BigDecimal after = getById(wallet.getId()).getBalance();
        recordLog(employerId, WalletLogType.SERVICE_FEE, amount.negate(), after, bizId, remark);
        return Result.ok(after);
    }

    // ---------- 私有辅助 ----------

    /** 金额校验：非空且大于 0。返回 null 表示通过，否则为失败 Result。 */
    private Result positive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0
                ? null
                : Result.fail("金额需大于 0");
    }

    /** 金额展示：去掉多余的 0（900.00 → 900，50.50 → 50.50）。 */
    private String money(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    /**
     * 取某用户的钱包，不存在则自动开户（0 余额）。
     */
    private Wallet ensureWallet(Long userId) {
        Wallet wallet = lambdaQuery().eq(Wallet::getUserId, userId).one();
        if (wallet == null) {
            wallet = new Wallet();
            wallet.setUserId(userId);
            wallet.setBalance(BigDecimal.ZERO);
            save(wallet);
        }
        return wallet;
    }

    /**
     * 记一条余额变动流水。
     */
    private void recordLog(Long userId, String type, BigDecimal amount, BigDecimal balanceAfter,
                           Long bizId, String remark) {
        WalletLog log = new WalletLog();
        log.setUserId(userId);
        log.setType(type);
        log.setAmount(amount);
        log.setBalanceAfter(balanceAfter);
        log.setBizId(bizId);
        log.setRemark(remark);
        walletLogMapper.insert(log);
    }

    private WalletLogDTO toDTO(WalletLog log) {
        WalletLogDTO dto = new WalletLogDTO();
        dto.setId(log.getId());
        dto.setType(log.getType());
        dto.setAmount(log.getAmount());
        dto.setBalanceAfter(log.getBalanceAfter());
        dto.setBizId(log.getBizId());
        dto.setRemark(log.getRemark());
        dto.setCreateTime(log.getCreateTime() == null ? null : log.getCreateTime().toString());
        return dto;
    }
}
