package com.linggong.tools;

import cn.hutool.json.JSONUtil;
import com.linggong.dto.Result;
import com.linggong.dto.UserDTO;
import com.linggong.service.IWalletService;
import com.linggong.utils.AiContextHelper;
import com.linggong.utils.UserHolder;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 工具：钱包查询。
 *
 * <p>只读查询当前登录用户的钱包，返回 Result JSON（含 success/errorMsg/data），
 * LLM 从 JSON 里取余额数据作答，不编造。
 * 注意：工具在非请求线程执行，登录用户从 {@code @ToolMemoryId}（服务端推导）反解，见 {@link AiContextHelper}。
 */
@Slf4j
@Component
public class WalletTool {

    private final IWalletService walletService;

    public WalletTool(IWalletService walletService) {
        this.walletService = walletService;
    }

    @Tool("查询当前登录用户的钱包余额（余额单位：元）")
    public String queryMyWallet(@ToolMemoryId String memoryId) {
        UserDTO user = AiContextHelper.userFromMemoryId(memoryId);
        log.info("[WalletTool] 查询钱包, memoryId={}", memoryId);
        if (user == null) {
            return JSONUtil.toJsonStr(Result.fail("无法识别当前登录用户，请重新登录"));
        }
        UserHolder.saveUser(user);
        try {
            return JSONUtil.toJsonStr(walletService.me());
        } catch (Exception e) {
            log.warn("[WalletTool] 查询钱包失败, userId={}", user.getId(), e);
            return JSONUtil.toJsonStr(Result.fail("钱包查询失败"));
        } finally {
            UserHolder.remove();
        }
    }
}
