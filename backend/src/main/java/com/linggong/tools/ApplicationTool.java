package com.linggong.tools;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linggong.dto.Result;
import com.linggong.dto.UserDTO;
import com.linggong.entity.Job;
import com.linggong.entity.JobApplication;
import com.linggong.mapper.JobApplicationMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.utils.AiContextHelper;
import com.linggong.utils.UserHolder;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 工具：报名记录查询。
 *
 * <p>直查报名表 + 批量拼岗位名（口径同 JobApplicationServiceImpl.myApplications），
 * 支持按状态过滤：0-待确认 1-已录用 2-已完成 3-已取消。
 *
 * <p>输出按白名单字段组装（只给 jobId/jobName/status/address/salary），刻意不含
 * createTime 等日期字段——实测 LLM 会把真实日期篡改成编造值（2026-09-08 → 2023-07-10），
 * 宁可让模型说「没有报名日期」，也不要它给出错误日期。
 */
@Slf4j
@Component
public class ApplicationTool {

    private final JobApplicationMapper jobApplicationMapper;
    private final JobMapper jobMapper;

    public ApplicationTool(JobApplicationMapper jobApplicationMapper, JobMapper jobMapper) {
        this.jobApplicationMapper = jobApplicationMapper;
        this.jobMapper = jobMapper;
    }

    @Tool("查询当前登录打工人（role=0）的报名记录，支持按状态过滤：status 0-待确认 1-已录用 2-已完成 3-已取消，不传则查全部（最多 20 条）")
    public String queryMyApplications(@ToolMemoryId String memoryId, Integer status) {
        UserDTO user = AiContextHelper.userFromMemoryId(memoryId);
        log.info("[ApplicationTool] 查询我的报名, memoryId={}, status={}", memoryId, status);
        if (user == null) {
            return JSONUtil.toJsonStr(Result.fail("无法识别当前登录用户，请重新登录"));
        }
        Long userId = user.getId();
        UserHolder.saveUser(user);
        try {
            List<JobApplication> apps = jobApplicationMapper.selectList(
                    new LambdaQueryWrapper<JobApplication>()
                            .eq(JobApplication::getWorkerId, userId)
                            .eq(status != null, JobApplication::getStatus, status)
                            .orderByDesc(JobApplication::getCreateTime)
                            .last("LIMIT 20"));
            if (CollUtil.isEmpty(apps)) {
                return JSONUtil.toJsonStr(Result.ok(Collections.emptyList(), 0L));
            }
            Map<Long, Job> jobMap = jobMapper.selectBatchIds(
                            apps.stream().map(JobApplication::getJobId).collect(Collectors.toList()))
                    .stream().collect(Collectors.toMap(Job::getId, j -> j));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (JobApplication app : apps) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("jobId", app.getJobId());
                row.put("status", app.getStatus());
                Job job = jobMap.get(app.getJobId());
                if (job != null) {
                    row.put("jobName", job.getName());
                    row.put("address", job.getAddress());
                    row.put("salary", job.getSalary());
                }
                rows.add(row);
            }
            return JSONUtil.toJsonStr(Result.ok(rows, (long) rows.size()));
        } catch (Exception e) {
            log.warn("[ApplicationTool] 查询报名失败, userId={}", userId, e);
            return JSONUtil.toJsonStr(Result.fail("报名查询失败"));
        } finally {
            UserHolder.remove();
        }
    }
}
