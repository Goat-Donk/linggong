package com.linggong.controller;

import com.linggong.annotation.RateLimiter;
import com.linggong.dto.AiOptimizeRequest;
import com.linggong.dto.Result;
import com.linggong.service.IAiOptimizeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 能力接口（业务类）。
 *
 * <p>注意：/ai 前缀不在 MvcConfig 白名单，接口一律要求登录；是否雇主在 service 内校验。
 */
@Tag(name = "AI 能力")
@RestController
@RequestMapping("/ai")
public class AiController {

    private final IAiOptimizeService aiOptimizeService;

    public AiController(IAiOptimizeService aiOptimizeService) {
        this.aiOptimizeService = aiOptimizeService;
    }

    /**
     * AI 优化 / 生成岗位描述（仅雇主 role=1）。草稿为空时根据表单字段生成，非空时润色补全。
     */
    @Operation(summary = "AI 优化岗位描述（仅雇主）")
    @RateLimiter(window = 60, limit = 10, type = RateLimiter.LimitType.USER, message = "优化过于频繁，请稍后再试")
    @PostMapping("/job-description/optimize")
    public Result optimizeJobDescription(@Valid @RequestBody AiOptimizeRequest request) {
        return aiOptimizeService.optimizeJobDescription(request);
    }
}
