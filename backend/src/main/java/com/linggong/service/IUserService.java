package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.LoginFormDTO;
import com.linggong.dto.Result;
import com.linggong.entity.User;

/**
 * 用户服务接口。
 */
public interface IUserService extends IService<User> {

    /**
     * 发送登录验证码（存 Redis，2 分钟有效）。
     */
    Result sendCode(String phone);

    /**
     * 手机号 + 验证码登录（用户不存在则自动注册），成功返回 token。
     */
    Result login(LoginFormDTO loginForm);

    /**
     * 退出登录（删除 Redis 中的 token）。
     */
    Result logout(String token);
}
