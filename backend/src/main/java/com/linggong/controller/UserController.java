package com.linggong.controller;

import cn.hutool.core.bean.BeanUtil;
import com.linggong.dto.LoginFormDTO;
import com.linggong.dto.Result;
import com.linggong.dto.UserDTO;
import com.linggong.dto.UserUpdateDTO;
import com.linggong.entity.User;
import com.linggong.entity.UserInfo;
import com.linggong.service.IUserInfoService;
import com.linggong.service.IUserService;
import com.linggong.utils.UserHolder;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口：登录认证 + 个人资料。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final IUserService userService;
    private final IUserInfoService userInfoService;

    public UserController(IUserService userService, IUserInfoService userInfoService) {
        this.userService = userService;
        this.userInfoService = userInfoService;
    }

    /**
     * 发送登录验证码（模拟短信，验证码存 Redis，2 分钟有效）。
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    /**
     * 手机号 + 验证码登录，成功返回 token。
     */
    @PostMapping("/login")
    public Result login(@Valid @RequestBody LoginFormDTO loginForm) {
        return userService.login(loginForm);
    }

    /**
     * 获取当前登录用户（从 ThreadLocal 取，不查库）。
     */
    @GetMapping("/me")
    public Result me() {
        return Result.ok(UserHolder.getUser());
    }

    /**
     * 退出登录（删除 Redis 中的 token）。
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token) {
        return userService.logout(token);
    }

    /**
     * 查看他人主页（昵称 + 头像）。
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }

    /**
     * 查看他人资料（简介/年龄/性别/信用分）。
     */
    @GetMapping("/info/{id}")
    public Result queryUserInfo(@PathVariable("id") Long userId) {
        UserInfo info = userInfoService.getByUserId(userId);
        // 没填过资料时返回空，前端展示"暂无资料"
        return Result.ok(info);
    }

    /**
     * 修改个人资料（昵称/头像 + 简介/年龄/性别）。
     *
     * <p>跨两张表（tb_user / tb_user_info），这里做简单编排；
     * 信用分 credit 不在此处修改，由互评阶段系统维护。
     */
    @PutMapping("/update")
    public Result update(@RequestHeader(value = "authorization", required = false) String token,
                         @Valid @RequestBody UserUpdateDTO updateDTO) {
        Long userId = UserHolder.getUser().getId();

        // 1. 更新用户表（昵称/头像）
        User user = new User();
        user.setId(userId);
        user.setNickName(updateDTO.getNickName());
        user.setIcon(updateDTO.getIcon());
        userService.updateById(user);

        // 2. 更新资料表（简介/年龄/性别，不存在则插入）
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setIntroduce(updateDTO.getIntroduce());
        userInfo.setAge(updateDTO.getAge());
        userInfo.setGender(updateDTO.getGender());
        userInfoService.saveOrUpdateByUserId(userInfo);

        // 3. 刷新 token 缓存的用户信息，避免 /user/me 返回旧昵称
        userService.refreshUserCache(token, userId);

        return Result.ok();
    }
}
