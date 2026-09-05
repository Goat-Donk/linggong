package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.entity.UserInfo;

/**
 * 用户资料服务接口。
 */
public interface IUserInfoService extends IService<UserInfo> {

    /**
     * 按 userId（注意：不是主键 id）查询用户资料。
     */
    UserInfo getByUserId(Long userId);

    /**
     * 保存或更新用户资料：按 userId 判断，不存在则插入，存在则更新。
     */
    void saveOrUpdateByUserId(UserInfo userInfo);
}
