package com.linggong.tools;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linggong.dto.Result;
import com.linggong.entity.Job;
import com.linggong.entity.JobSettlement;
import com.linggong.entity.JobSettlementItem;
import com.linggong.mapper.JobMapper;
import com.linggong.mapper.JobSettlementItemMapper;
import com.linggong.mapper.JobSettlementMapper;
import com.linggong.utils.UserHolder;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 工具：结算工资记录查询。
 *
 * <p>平台没有「我的结算」列表接口（结算详情按岗位查且仅雇主可见），
 * 这里直查结算明细表，按 workerId 过滤后拼岗位名/结算时间，只读不落库。
 */
@Slf4j
@Component
public class SettlementTool {

    private final JobSettlementItemMapper settlementItemMapper;
    private final JobSettlementMapper settlementMapper;
    private final JobMapper jobMapper;

    public SettlementTool(JobSettlementItemMapper settlementItemMapper,
                          JobSettlementMapper settlementMapper,
                          JobMapper jobMapper) {
        this.settlementItemMapper = settlementItemMapper;
        this.settlementMapper = settlementMapper;
        this.jobMapper = jobMapper;
    }

    @Tool("查询当前登录打工人（role=0）的历史结算工资记录（已到账明细，最多 20 条）")
    public String queryMySettlements() {
        Long userId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        log.info("[SettlementTool] 查询我的结算工资, userId={}", userId);
        if (userId == null) {
            return JSONUtil.toJsonStr(Result.fail("用户未登录，无法查询结算记录"));
        }
        try {
            List<JobSettlementItem> items = settlementItemMapper.selectList(
                    new LambdaQueryWrapper<JobSettlementItem>()
                            .eq(JobSettlementItem::getWorkerId, userId)
                            .orderByDesc(JobSettlementItem::getCreateTime)
                            .last("LIMIT 20"));
            if (CollUtil.isEmpty(items)) {
                return JSONUtil.toJsonStr(Result.ok(Collections.emptyList(), 0L));
            }
            Map<Long, JobSettlement> settlementMap = settlementMapper.selectBatchIds(
                            items.stream().map(JobSettlementItem::getSettlementId).distinct().collect(Collectors.toList()))
                    .stream().collect(Collectors.toMap(JobSettlement::getId, s -> s));
            Map<Long, Job> jobMap = jobMapper.selectBatchIds(
                            settlementMap.values().stream().map(JobSettlement::getJobId).distinct().collect(Collectors.toList()))
                    .stream().collect(Collectors.toMap(Job::getId, j -> j));

            List<Map<String, Object>> list = new ArrayList<>();
            for (JobSettlementItem item : items) {
                JobSettlement settlement = settlementMap.get(item.getSettlementId());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("jobName", jobName(jobMap, settlement));
                row.put("wageAmount", item.getWageAmount());
                row.put("paidHalfDays", item.getPaidHalfDays());
                row.put("settledAt", settlement == null ? null : settlement.getSettledAt());
                list.add(row);
            }
            return JSONUtil.toJsonStr(Result.ok(list, (long) list.size()));
        } catch (Exception e) {
            log.warn("[SettlementTool] 查询结算工资失败, userId={}", userId, e);
            return JSONUtil.toJsonStr(Result.fail("结算记录查询失败"));
        }
    }

    private String jobName(Map<Long, Job> jobMap, JobSettlement settlement) {
        if (settlement == null) {
            return "未知岗位";
        }
        Job job = jobMap.get(settlement.getJobId());
        return job == null ? "岗位#" + settlement.getJobId() : job.getName();
    }
}
