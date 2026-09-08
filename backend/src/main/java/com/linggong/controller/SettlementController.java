package com.linggong.controller;

import com.linggong.dto.Result;
import com.linggong.service.ISettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗位结算接口：雇主查看结算预览、手动提前结算。到期自动结算由定时任务驱动。
 */
@Tag(name = "结算接口", description = "雇主查看结算预览 / 手动提前结算（到期自动结算由定时任务兜底）")
@RestController
@RequestMapping("/settlement")
public class SettlementController {

    private final ISettlementService settlementService;

    public SettlementController(ISettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Operation(summary = "雇主查看岗位结算预览（未结算）或结果（已结算）")
    @GetMapping("/detail")
    public Result detail(@Parameter(description = "岗位 id") @RequestParam("jobId") Long jobId) {
        return settlementService.detail(jobId);
    }

    @Operation(summary = "雇主手动提前结算岗位（任意时刻，幂等）")
    @PostMapping("/settle")
    public Result settle(@Parameter(description = "岗位 id") @RequestParam("jobId") Long jobId) {
        return settlementService.settle(jobId);
    }
}
