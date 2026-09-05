package com.linggong.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布动态入参。userId 由登录态注入，不接受前端传参，防止伪造。
 */
@Data
public class BlogFormDTO {

    /** 标题（可空） */
    @Size(max = 64, message = "标题最长 64 字")
    private String title;

    /** 内容（必填） */
    @NotBlank(message = "内容不能为空")
    @Size(max = 2048, message = "内容最长 2048 字")
    private String content;

    /** 图片列表，逗号分隔（可空） */
    @Size(max = 2048, message = "图片列表过长")
    private String images;
}
