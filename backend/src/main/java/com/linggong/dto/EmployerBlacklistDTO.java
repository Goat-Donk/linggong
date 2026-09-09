package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 雇主黑名单展示对象：黑名单记录 + 被打工人信息（供「黑名单管理」列表展示与解除）。
 */
@Data
@Schema(description = "雇主黑名单展示对象")
public class EmployerBlacklistDTO {

    /** 黑名单记录 id */
    private Long id;

    /** 被打工人 id */
    private Long workerId;

    /** 被打工人昵称 */
    private String workerName;

    /** 被打工人头像 */
    private String workerIcon;

    /** 拉黑原因 */
    private String reason;

    /** 拉黑时间 */
    private LocalDateTime createTime;
}
