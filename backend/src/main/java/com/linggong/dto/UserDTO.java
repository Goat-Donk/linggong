package com.linggong.dto;

import lombok.Data;

/**
 * 安全返回的用户信息（存入 Redis token、返回给前端）。
 *
 * <p>与 {@code User} 实体不同，这里只暴露必要字段，不暴露手机号等敏感信息。
 */
@Data
public class UserDTO {

    private Long id;

    private String nickName;

    private String icon;
}
