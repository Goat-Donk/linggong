package com.linggong.controller;

import com.linggong.dto.BlacklistFormDTO;
import com.linggong.dto.Result;
import com.linggong.service.IEmployerBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 雇主拉黑接口：拉黑 / 解除 / 我的黑名单。均仅雇主可用（需登录）。
 */
@Tag(name = "雇主拉黑接口", description = "雇主拉黑打工人：拉黑 / 解除 / 黑名单管理")
@RestController
@RequestMapping("/employer-blacklist")
public class EmployerBlacklistController {

    private final IEmployerBlacklistService blacklistService;

    public EmployerBlacklistController(IEmployerBlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    /**
     * 拉黑一名打工人（仅雇主）。侧效：自动取消该工人对我所有岗位的待确认报名。
     */
    @Operation(summary = "拉黑一名打工人（仅雇主）")
    @PostMapping
    public Result add(@Valid @RequestBody BlacklistFormDTO form) {
        return blacklistService.add(form);
    }

    /**
     * 解除拉黑（仅雇主），解除后该工人可再次报名。
     */
    @Operation(summary = "解除拉黑（仅雇主）")
    @DeleteMapping("/{workerId}")
    public Result remove(@Parameter(description = "被打工人 id") @PathVariable("workerId") Long workerId) {
        return blacklistService.remove(workerId);
    }

    /**
     * 我的黑名单（分页）。
     */
    @Operation(summary = "我的黑名单（分页）")
    @GetMapping
    public Result myList(@Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                         @Parameter(description = "每页条数，默认 10") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return blacklistService.myList(page, pageSize);
    }
}
