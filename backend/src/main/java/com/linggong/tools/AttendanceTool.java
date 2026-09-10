package com.linggong.tools;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linggong.dto.Result;
import com.linggong.dto.UserDTO;
import com.linggong.entity.Attendance;
import com.linggong.entity.Job;
import com.linggong.mapper.AttendanceMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.utils.AiContextHelper;
import com.linggong.utils.AiToolDate;
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
 * Agent 工具：打工人我的考勤查询。
 *
 * <p>注意：{@code IAttendanceService.myAttendanceJobs()} 是雇主视角（看自己发布的岗位），
 * 不能给打工人用。这里直查 tb_attendance 按 workerId 过滤，再批量拼岗位名，只读不落库。
 * 日期用 {@link AiToolDate} 预格式化为中文串，避免 LLM 篡改。
 */
@Slf4j
@Component
public class AttendanceTool {

    private final AttendanceMapper attendanceMapper;
    private final JobMapper jobMapper;

    public AttendanceTool(AttendanceMapper attendanceMapper, JobMapper jobMapper) {
        this.attendanceMapper = attendanceMapper;
        this.jobMapper = jobMapper;
    }

    @Tool("查询当前登录打工人（role=0）的考勤记录（一人一岗一天一条），返回字段：jobId 岗位id、jobName 岗位名称、workDate 出勤日期、onStatus 到岗核销状态、offStatus 下工核销状态；onStatus/offStatus：0-未申请 1-待核销 2-通过 3-驳回")
    public String queryMyAttendances(@ToolMemoryId String memoryId) {
        UserDTO user = AiContextHelper.userFromMemoryId(memoryId);
        log.info("[AttendanceTool] 查询我的考勤, memoryId={}", memoryId);
        if (user == null) {
            return JSONUtil.toJsonStr(Result.fail("无法识别当前登录用户，请重新登录"));
        }
        Long userId = user.getId();
        UserHolder.saveUser(user);
        try {
            List<Attendance> atts = attendanceMapper.selectList(
                    new LambdaQueryWrapper<Attendance>()
                            .eq(Attendance::getWorkerId, userId)
                            .orderByDesc(Attendance::getWorkDate)
                            .last("LIMIT 20"));
            if (CollUtil.isEmpty(atts)) {
                return JSONUtil.toJsonStr(Result.ok(Collections.emptyList(), 0L));
            }
            Map<Long, Job> jobMap = jobMapper.selectBatchIds(
                            atts.stream().map(Attendance::getJobId).distinct().collect(Collectors.toList()))
                    .stream().collect(Collectors.toMap(Job::getId, j -> j));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Attendance att : atts) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("jobId", att.getJobId());
                Job job = jobMap.get(att.getJobId());
                row.put("jobName", job == null ? "未知岗位" : job.getName());
                row.put("workDate", AiToolDate.day(att.getWorkDate()));
                row.put("onStatus", att.getOnStatus());
                row.put("offStatus", att.getOffStatus());
                rows.add(row);
            }
            return JSONUtil.toJsonStr(Result.ok(rows, (long) rows.size()));
        } catch (Exception e) {
            log.warn("[AttendanceTool] 查询考勤失败, userId={}", userId, e);
            return JSONUtil.toJsonStr(Result.fail("考勤查询失败"));
        } finally {
            UserHolder.remove();
        }
    }
}
