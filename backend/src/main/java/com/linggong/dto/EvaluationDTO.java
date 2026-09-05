package com.linggong.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 互评展示对象：评价字段 + 评价人/被评价人简要信息。
 */
@Data
public class EvaluationDTO {

    private Long id;

    private Long jobId;

    private Long fromUserId;

    private Long toUserId;

    private Integer rating;

    private String content;

    private LocalDateTime createTime;

    /** 评价人昵称 */
    private String fromNickName;

    /** 评价人头像 */
    private String fromIcon;

    /** 被评价人昵称 */
    private String toNickName;

    /** 被评价人头像 */
    private String toIcon;
}
