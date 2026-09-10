package com.linggong.tools;

import cn.hutool.json.JSONUtil;
import com.linggong.dto.Result;
import com.linggong.dto.UserDTO;
import com.linggong.entity.Job;
import com.linggong.service.IJobService;
import com.linggong.utils.AiContextHelper;
import com.linggong.utils.AiToolDate;
import com.linggong.utils.UserHolder;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 工具：岗位详情查询。
 *
 * <p>复用 IJobService.queryById（含缓存/浏览量逻辑），只读；岗位数据对登录用户可见即可返回。
 * 输出按白名单字段组装（名称/日薪/名额/时间/地址/描述/状态），去掉 createTime 等
 * 易被 LLM 篡改的字段，起止时间预格式化为中文串。
 * 注意：工具在非请求线程执行，登录用户从 {@code @ToolMemoryId}（服务端推导）反解，见 {@link AiContextHelper}。
 */
@Slf4j
@Component
public class JobTool {

    private final IJobService jobService;

    public JobTool(IJobService jobService) {
        this.jobService = jobService;
    }

    @Tool("按岗位 id 查询岗位详情（名称/日薪/名额/开始结束时间/地址/描述/状态），传入 jobId")
    public String queryJobDetail(@ToolMemoryId String memoryId, Long jobId) {
        UserDTO user = AiContextHelper.userFromMemoryId(memoryId);
        if (user == null) {
            return JSONUtil.toJsonStr(Result.fail("无法识别当前登录用户，请重新登录"));
        }
        log.info("[JobTool] 查询岗位详情, jobId={}", jobId);
        if (jobId == null) {
            return JSONUtil.toJsonStr(Result.fail("缺少岗位 id"));
        }
        UserHolder.saveUser(user);
        try {
            Result result = jobService.queryById(jobId);
            Object data = result.getData();
            if (data == null) {
                return JSONUtil.toJsonStr(result);
            }
            Job job = (Job) data;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", job.getId());
            row.put("name", job.getName());
            row.put("salary", job.getSalary());
            row.put("headcount", job.getHeadcount());
            row.put("startTime", AiToolDate.datetime(job.getStartTime()));
            row.put("endTime", AiToolDate.datetime(job.getEndTime()));
            row.put("address", job.getAddress());
            row.put("description", job.getDescription());
            // 岗位状态 0=上架 1=下架，直接给中文避免模型误当报名状态转述
            row.put("status", job.getStatus() == null ? null : (job.getStatus() == 0 ? "上架（招募中）" : "已下架"));
            return JSONUtil.toJsonStr(Result.ok(row));
        } catch (Exception e) {
            log.warn("[JobTool] 查询岗位失败, jobId={}", jobId, e);
            return JSONUtil.toJsonStr(Result.fail("岗位查询失败"));
        } finally {
            UserHolder.remove();
        }
    }
}
