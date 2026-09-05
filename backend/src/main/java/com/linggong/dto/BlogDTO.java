package com.linggong.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态展示对象：动态字段 + 当前用户是否点赞 + 发布者简要信息。
 */
@Data
public class BlogDTO {

    private Long id;

    private Long userId;

    private String title;

    private String content;

    private String images;

    private Integer liked;

    private LocalDateTime createTime;

    /** 当前登录用户是否已点赞 */
    private Boolean isLike;

    /** 发布者头像 */
    private String icon;

    /** 发布者昵称 */
    private String nickName;
}
