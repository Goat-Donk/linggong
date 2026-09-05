package com.linggong.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料入参。
 *
 * <p>字段均可选：为 null 的字段不会被更新（MyBatis-Plus 默认跳过 null）。
 * 信用分 credit 不在此处，由系统管理（互评阶段自动增减），不允许用户自行修改。
 */
@Data
public class UserUpdateDTO {

    /** 昵称 */
    @Size(max = 32, message = "昵称不能超过 32 字")
    private String nickName;

    /** 头像地址 */
    private String icon;

    /** 年龄 */
    private Integer age;

    /** 性别：0 未知，1 男，2 女 */
    private Integer gender;

    /** 个人简介 */
    @Size(max = 255, message = "简介不能超过 255 字")
    private String introduce;
}
