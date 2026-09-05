package com.linggong.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 登录表单：手机号 + 验证码。
 */
@Data
public class LoginFormDTO {

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String phone;

    /** 验证码 */
    @NotBlank(message = "验证码不能为空")
    private String code;
}
