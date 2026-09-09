package com.linggong.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.Result;
import com.linggong.entity.CreditLog;
import com.linggong.entity.UserInfo;
import com.linggong.mapper.CreditLogMapper;
import com.linggong.mapper.UserInfoMapper;
import com.linggong.service.IUserInfoService;
import com.linggong.utils.CreditRules;
import com.linggong.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户资料服务实现。
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

    private final CreditLogMapper creditLogMapper;

    public UserInfoServiceImpl(CreditLogMapper creditLogMapper) {
        this.creditLogMapper = creditLogMapper;
    }

    @Override
    public UserInfo getByUserId(Long userId) {
        return lambdaQuery().eq(UserInfo::getUserId, userId).one();
    }

    @Override
    public void saveOrUpdateByUserId(UserInfo userInfo) {
        UserInfo existing = getByUserId(userInfo.getUserId());
        if (existing == null) {
            // 首次设置资料：插入（credit 为 null 时走 DB 默认 100）
            save(userInfo);
        } else {
            // 已存在：更新
            userInfo.setId(existing.getId());
            updateById(userInfo);
        }
    }

    @Override
    @Transactional
    public void adjustCredit(Long userId, int delta, String reasonType, Long bizId, String remark) {
        if (userId == null || delta == 0) {
            return;
        }
        UserInfo existing = getByUserId(userId);
        int base = existing != null && existing.getCredit() != null ? existing.getCredit() : CreditRules.DEFAULT;
        int updated = CreditRules.clamp(base + delta);
        int applied = updated - base; // clamp 后的真实变动（已在上下限则可为 0）
        if (existing == null) {
            // 尚无资料行：新建并写入调整后的信用分
            UserInfo info = new UserInfo();
            info.setUserId(userId);
            info.setCredit(updated);
            save(info);
        } else {
            if (applied != 0) {
                // 已存在且分值确有变动：仅更新信用分（updateById 默认忽略 null 字段）
                UserInfo update = new UserInfo();
                update.setId(existing.getId());
                update.setCredit(updated);
                updateById(update);
            }
        }
        // 记信用流水：clamp 后真实变动为 0（已在上下限）则不产生流水，避免无意义记录
        if (applied != 0) {
            CreditLog log = new CreditLog();
            log.setUserId(userId);
            log.setReasonType(reasonType);
            log.setChangeAmount(applied);
            log.setAfterCredit(updated);
            log.setBizId(bizId);
            log.setRemark(remark);
            creditLogMapper.insert(log);
        }
    }

    @Override
    public int myCredit() {
        UserInfo info = getByUserId(UserHolder.getUser().getId());
        return info != null && info.getCredit() != null ? info.getCredit() : CreditRules.DEFAULT;
    }

    @Override
    public Result creditLogs(Integer page, Integer pageSize) {
        Long userId = UserHolder.getUser().getId();
        Page<CreditLog> result = creditLogMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<CreditLog>()
                        .eq(CreditLog::getUserId, userId)
                        .orderByDesc(CreditLog::getCreateTime, CreditLog::getId));
        return Result.ok(result.getRecords(), result.getTotal());
    }

    @Override
    public void incrementBreakCount(Long userId) {
        baseMapper.incrementBreakCount(userId);
    }

    @Override
    public Map<Long, UserInfo> batchByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserInfo> list = lambdaQuery().in(UserInfo::getUserId, userIds).list();
        return list.stream().collect(Collectors.toMap(UserInfo::getUserId, Function.identity()));
    }
}
