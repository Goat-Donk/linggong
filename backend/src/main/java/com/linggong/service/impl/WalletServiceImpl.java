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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 虚拟钱包服务实现。
 */
@Service
public class WalletServiceImpl extends ServiceImpl<WalletMapper, Wallet> implements IWalletService {

    /** 单次充值上限（元），防手滑大额 */
    private static final int MAX_RECHARGE = 1_000_000;

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
        if (amount > MAX_RECHARGE) {
            return Result.fail("单次充值不能超过 " + MAX_RECHARGE + " 元");
        }
        Long userId = UserHolder.getUser().getId();
        Wallet wallet = ensureWallet(userId);
        // 余额用 SQL 原子自增，避免「读-改-写」并发丢更新
        lambdaUpdate()
                .setSql("balance = balance + " + amount)
                .eq(Wallet::getId, wallet.getId())
                .update();
        int after = getById(wallet.getId()).getBalance();
        recordLog(userId, WalletLogType.RECHARGE, amount, after, null, "模拟充值入账");
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
    public int balanceOf(Long userId) {
        Wallet wallet = lambdaQuery().eq(Wallet::getUserId, userId).one();
        return wallet == null || wallet.getBalance() == null ? 0 : wallet.getBalance();
    }

    @Override
    @Transactional
    public Result freeze(Long userId, Long bizId, int amount, String remark) {
        if (amount <= 0) {
            return Result.fail("冻结金额需大于 0");
        }
        Wallet wallet = ensureWallet(userId);
        if (wallet.getBalance() < amount) {
            return Result.fail("可用余额不足，无法冻结 ¥" + amount
                    + "（当前可用 ¥" + wallet.getBalance() + "，请先到「我的钱包」充值）");
        }
        lambdaUpdate()
                .setSql("balance = balance - " + amount)
                .eq(Wallet::getId, wallet.getId())
                .update();
        int after = getById(wallet.getId()).getBalance();
        recordLog(userId, WalletLogType.FREEZE, -amount, after, bizId, remark);
        return Result.ok(after);
    }

    @Override
    @Transactional
    public Result unfreeze(Long userId, Long bizId, int amount, String remark) {
        if (amount <= 0) {
            return Result.fail("解冻金额需大于 0");
        }
        Wallet wallet = ensureWallet(userId);
        lambdaUpdate()
                .setSql("balance = balance + " + amount)
                .eq(Wallet::getId, wallet.getId())
                .update();
        int after = getById(wallet.getId()).getBalance();
        recordLog(userId, WalletLogType.UNFREEZE, amount, after, bizId, remark);
        return Result.ok(after);
    }

    /**
     * 取某用户的钱包，不存在则自动开户（0 余额）。
     */
    private Wallet ensureWallet(Long userId) {
        Wallet wallet = lambdaQuery().eq(Wallet::getUserId, userId).one();
        if (wallet == null) {
            wallet = new Wallet();
            wallet.setUserId(userId);
            wallet.setBalance(0);
            save(wallet);
        }
        return wallet;
    }

    /**
     * 记一条余额变动流水。
     */
    private void recordLog(Long userId, String type, int amount, int balanceAfter, Long bizId, String remark) {
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
