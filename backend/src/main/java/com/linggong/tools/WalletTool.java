package com.linggong.tools;

import cn.hutool.json.JSONUtil;
import com.linggong.dto.Result;
import com.linggong.service.IWalletService;
import com.linggong.utils.UserHolder;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 工具：钱包查询。
 *
 * <p>只读查询当前登录用户的钱包，返回 Result JSON（含 success/errorMsg/data），
 * LLM 从 JSON 里取余额数据作答，不编造。
 */
@Slf4j
@Component
public class WalletTool {

    private final IWalletService walletService;

    public WalletTool(IWalletService walletService) {
        this.walletService = walletService;
    }

    @Tool("查询当前登录用户的钱包余额（余额单位：元）")
    public String queryMyWallet() {
        Long userId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        log.info("[WalletTool] 查询钱包, userId={}", userId);
        if (userId == null) {
            return JSONUtil.toJsonStr(Result.fail("用户未登录，无法查询钱包"));
        }
        try {
            return JSONUtil.toJsonStr(walletService.me());
        } catch (Exception e) {
            log.warn("[WalletTool] 查询钱包失败, userId={}", userId, e);
            return JSONUtil.toJsonStr(Result.fail("钱包查询失败"));
        }
    }
}
