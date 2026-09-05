package com.linggong.utils;

import com.linggong.dto.UserDTO;

/**
 * 当前登录用户持有器（基于 ThreadLocal）。
 *
 * <p>拦截器在请求进入时把解析出的用户写入本类，业务代码通过 {@link #getUser()} 获取；
 * 请求结束后拦截器必须调用 {@link #remove()} 释放，防止线程复用导致内存泄漏或数据串号。
 */
public final class UserHolder {

    private static final ThreadLocal<UserDTO> TL = new ThreadLocal<>();

    /**
     * 保存当前登录用户。
     */
    public static void saveUser(UserDTO user) {
        TL.set(user);
    }

    /**
     * 获取当前登录用户（未登录时返回 null）。
     */
    public static UserDTO getUser() {
        return TL.get();
    }

    /**
     * 释放 ThreadLocal，必须在请求结束后调用。
     */
    public static void remove() {
        TL.remove();
    }

    private UserHolder() {
    }
}
