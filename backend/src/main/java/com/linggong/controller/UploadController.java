package com.linggong.controller;

import com.linggong.dto.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传接口：上传图片（头像 / 岗位图 / 晒单图），本地存储。
 *
 * <p>上传成功后返回可访问的 URL（/uploads/{文件名}），
 * 由 {@code MvcConfig.addResourceHandlers} 把 /uploads/** 映射到本地目录。
 */
@Tag(name = "文件上传接口", description = "上传图片（本地存储）")
@RestController
@RequestMapping("/upload")
public class UploadController {

    /** 允许的图片扩展名 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 本地上传目录（与 application.yml 的 linggong.upload.dir 一致，末尾带 /） */
    @Value("${linggong.upload.dir}")
    private String uploadDir;

    /**
     * 上传图片，返回可访问 URL。
     */
    @Operation(summary = "上传图片，返回可访问 URL")
    @PostMapping("/image")
    public Result uploadImage(@Parameter(description = "图片文件（jpg/jpeg/png/gif/webp）") @RequestParam("file") MultipartFile file) {
        // 1. 非空校验
        if (file == null || file.isEmpty()) {
            return Result.fail("文件不能为空");
        }
        // 2. 类型校验（只允许图片）
        String ext = extractExt(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXT.contains(ext)) {
            return Result.fail("仅支持图片格式：jpg/jpeg/png/gif/webp");
        }
        // 3. 生成唯一文件名，避免重名覆盖
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        // 4. 确保目录存在后保存
        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            return Result.fail("上传目录创建失败");
        }
        try {
            file.transferTo(new File(dir, filename).getAbsoluteFile());
        } catch (IOException e) {
            return Result.fail("文件上传失败");
        }
        // 5. 返回可访问 URL
        return Result.ok("/uploads/" + filename);
    }

    /**
     * 从原始文件名提取小写扩展名；没有扩展名返回 null。
     */
    private String extractExt(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
