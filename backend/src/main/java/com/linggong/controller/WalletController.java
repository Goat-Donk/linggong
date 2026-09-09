package com.linggong.controller;

import com.linggong.dto.Result;
import com.linggong.service.IWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 钱包接口：查看余额、模拟充值、流水列表。登录即可用（雇主/打工人共用）。
 *
 * <p>说明：发岗担保金冻结、工资结算的接口不在这里，分别在发岗/结算模块里通过
 * 钱包服务记账，这里只暴露钱包本身的操作。
 */
@Tag(name = "钱包接口", description = "查看余额、模拟充值、钱包流水")
@RestController
@RequestMapping("/wallet")
public class WalletController {

    private final IWalletService walletService;

    public WalletController(IWalletService walletService) {
        this.walletService = walletService;
    }

    /**
     * 查看我的钱包（首次访问自动开户，返回 0 余额账户）。
     */
    @Operation(summary = "查看我的钱包（自动开户）")
    @GetMapping("/me")
    public Result me() {
        return walletService.me();
    }

    /**
     * 模拟充值（开发/演示环境，直接加可用余额并记流水）。
     *
     * @param amount 充值金额（元），正整数
     */
    @Operation(summary = "模拟充值")
    @PostMapping("/recharge")
    public Result recharge(@Parameter(description = "充值金额（元）") @RequestParam("amount") Integer amount) {
        return walletService.recharge(amount);
    }

    /**
     * 模拟提现（可用余额减 amount 并记「提现」流水；余额不足/超上限返回失败）。
     *
     * @param amount 提现金额（元），正整数
     */
    @Operation(summary = "模拟提现")
    @PostMapping("/withdraw")
    public Result withdraw(@Parameter(description = "提现金额（元）") @RequestParam("amount") Integer amount) {
        return walletService.withdraw(amount);
    }

    /**
     * 我的钱包流水（分页，按时间倒序）。
     */
    @Operation(summary = "我的钱包流水（分页）")
    @GetMapping("/logs")
    public Result logs(@Parameter(description = "页码，默认 1") @RequestParam(value = "page", defaultValue = "1") Integer page,
                       @Parameter(description = "每页条数，默认 10") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return walletService.logs(page, pageSize);
    }
}
