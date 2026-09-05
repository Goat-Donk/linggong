package com.linggong.utils;

/**
 * 正则校验工具。
 */
public final class RegexUtils {

    /** 中国大陆手机号：1 开头，第二位 3~9，共 11 位 */
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    /**
     * 校验手机号是否非法。
     *
     * @param phone 手机号
     * @return true 表示非法（为 null 或格式不正确）
     */
    public static boolean isPhoneInvalid(String phone) {
        return phone == null || !phone.matches(PHONE_REGEX);
    }

    private RegexUtils() {
    }
}
