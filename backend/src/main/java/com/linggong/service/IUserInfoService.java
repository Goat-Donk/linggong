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

    /**
     * 增减用户信用分（无资料行则先建默认 100 再加减），结果收敛到 [0,100]。
     *
     * <p>互评按 {@link CreditRules#deltaByRating} 调被评价人；单方解除录用按
     * {@link CreditRules#BREAK_PENALTY} 扣发起方。信用分不允许用户自行修改。
     *
     * @param userId 目标用户
     * @param delta  增减分（正/负/0）
     */
    void adjustCredit(Long userId, int delta);
}
