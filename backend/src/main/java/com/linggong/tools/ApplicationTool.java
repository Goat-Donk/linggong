package com.linggong.tools;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linggong.dto.JobApplicationDTO;
import com.linggong.dto.Result;
import com.linggong.entity.Job;
import com.linggong.entity.JobApplication;
import com.linggong.mapper.JobApplicationMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.utils.UserHolder;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 工具：报名记录查询。
 *
 * <p>直查报名表 + 批量拼岗位名（口径同 JobApplicationServiceImpl.myApplications），
 * 支持按状态过滤：0-待确认 1-已录用 2-已完成 3-已取消。
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
    public String queryMyApplications(Integer status) {
        Long userId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        log.info("[ApplicationTool] 查询我的报名, userId={}, status={}", userId, status);
        if (userId == null) {
            return JSONUtil.toJsonStr(Result.fail("用户未登录，无法查询报名"));
        }
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
            List<JobApplicationDTO> dtos = apps.stream().map(app -> {
                JobApplicationDTO dto = BeanUtil.copyProperties(app, JobApplicationDTO.class);
                Job job = jobMap.get(app.getJobId());
                if (job != null) {
                    dto.setJobName(job.getName());
                    dto.setAddress(job.getAddress());
                    dto.setSalary(job.getSalary());
                }
                return dto;
            }).collect(Collectors.toList());
            return JSONUtil.toJsonStr(Result.ok(dtos, (long) dtos.size()));
        } catch (Exception e) {
            log.warn("[ApplicationTool] 查询报名失败, userId={}", userId, e);
            return JSONUtil.toJsonStr(Result.fail("报名查询失败"));
        }
    }
}
