package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 拉黑表单：雇主拉黑某打工人。
 */
@Data
@Schema(description = "拉黑表单")
public class BlacklistFormDTO {

    /** 被打工人 id */
    @NotNull(message = "请选择要拉黑的打工人")
    @Schema(description = "被打工人 id")
    private Long workerId;

    /** 拉黑原因（雇主侧可见，最长 100 字，可不填） */
    @Schema(description = "拉黑原因（最长 100 字，可不填）")
    private String reason;
}
