package com.linggong.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.entity.UserInfo;
import com.linggong.mapper.UserInfoMapper;
import com.linggong.service.IUserInfoService;
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
}
