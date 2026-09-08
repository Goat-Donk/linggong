package com.linggong.config;

import com.linggong.service.ISettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 到期自动结算兜底任务：每分钟扫描一次 end_time 已过、仍有冻结款且未结算的岗位，
 * 逐岗调用结算引擎。单岗失败（如雇主余额不足付服务费）不影响其他岗，下次任务自动重试。
 */
@Component
public class SettlementScheduleConfig {

    private static final Logger log = LoggerFactory.getLogger(SettlementScheduleConfig.class);

    private final ISettlementService settlementService;

    public SettlementScheduleConfig(ISettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 30_000)
    public void autoSettle() {
        try {
            settlementService.autoSettleExpired();
        } catch (Exception e) {
            // 兜底：单次调度异常不中断后续调度
            log.warn("到期自动结算调度异常：{}", e.getMessage());
        }
    }
}
