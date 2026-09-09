package com.linggong.controller;

import com.linggong.dto.Result;
import com.linggong.service.IUserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 信用分接口：查看当前信用分 + 信用变动流水。
 */
@Tag(name = "信用接口", description = "信用分详情 / 变动流水")
@RestController
@RequestMapping("/credit")
public class CreditController {

    private final IUserInfoService userInfoService;

    public CreditController(IUserInfoService userInfoService) {
        this.userInfoService = userInfoService;
    }

    @Operation(summary = "我的当前信用分")
    @GetMapping("/my")
    public Result myCredit() {
        return Result.ok(userInfoService.myCredit());
    }

    @Operation(summary = "我的信用变动流水（分页，按时间倒序）")
    @GetMapping("/logs")
    public Result logs(@Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                       @Parameter(description = "每页条数，默认 10") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return userInfoService.creditLogs(page, pageSize);
    }
}
