package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料入参。
 *
 * <p>字段均可选：为 null 的字段不会被更新（MyBatis-Plus 默认跳过 null）。
 * 信用分 credit 不在此处，由系统管理（互评阶段自动增减），不允许用户自行修改。
 */
@Data
@Schema(description = "修改个人资料入参（字段均可选，null 不更新）")
public class UserUpdateDTO {

    @Schema(description = "昵称（最长 32 字）")
    @Size(max = 32, message = "昵称不能超过 32 字")
    private String nickName;

    @Schema(description = "头像地址")
    private String icon;

    @Schema(description = "年龄")
    private Integer age;

    @Schema(description = "性别：0 未知，1 男，2 女")
    private Integer gender;

    @Schema(description = "个人简介（最长 255 字）")
    @Size(max = 255, message = "简介不能超过 255 字")
    private String introduce;
}
