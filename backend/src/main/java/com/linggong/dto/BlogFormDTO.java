package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布动态入参。userId 由登录态注入，不接受前端传参，防止伪造。
 */
@Data
@Schema(description = "发布动态入参")
public class BlogFormDTO {

    @Schema(description = "标题（可空，最长 64 字）")
    @Size(max = 64, message = "标题最长 64 字")
    private String title;

    @Schema(description = "内容（必填，最长 2048 字）")
    @NotBlank(message = "内容不能为空")
    @Size(max = 2048, message = "内容最长 2048 字")
    private String content;

    @Schema(description = "图片列表，逗号分隔（可空）")
    @Size(max = 2048, message = "图片列表过长")
    private String images;
}
