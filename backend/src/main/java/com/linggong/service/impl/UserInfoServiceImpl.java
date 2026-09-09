package com.linggong.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.entity.UserInfo;
import com.linggong.mapper.UserInfoMapper;
import com.linggong.service.IUserInfoService;
import com.linggong.utils.CreditRules;
import org.springframework.stereotype.Service;

/**
 * 用户资料服务实现。
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

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
    public void adjustCredit(Long userId, int delta) {
        if (userId == null || delta == 0) {
            return;
        }
        UserInfo existing = getByUserId(userId);
        int base = existing != null && existing.getCredit() != null ? existing.getCredit() : CreditRules.DEFAULT;
        int updated = CreditRules.clamp(base + delta);
        if (existing == null) {
            // 尚无资料行：新建并写入调整后的信用分
            UserInfo info = new UserInfo();
            info.setUserId(userId);
            info.setCredit(updated);
            save(info);
        } else {
            // 已存在：仅更新信用分（updateById 默认忽略 null 字段，不会误清其它字段）
            UserInfo update = new UserInfo();
            update.setId(existing.getId());
            update.setCredit(updated);
            updateById(update);
        }
    }
}
