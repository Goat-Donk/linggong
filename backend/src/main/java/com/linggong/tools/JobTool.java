package com.linggong.tools;

import cn.hutool.json.JSONUtil;
import com.linggong.dto.Result;
import com.linggong.service.IJobService;
import com.linggong.utils.UserHolder;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 工具：岗位详情查询。
 *
 * <p>复用 IJobService.queryById（含缓存/浏览量逻辑），只读；岗位数据对登录用户可见即可返回。
 */
@Slf4j
@Component
public class JobTool {

    private final IJobService jobService;

    public JobTool(IJobService jobService) {
        this.jobService = jobService;
    }

    @Tool("按岗位 id 查询岗位详情（名称/日薪/名额/时间/地址/描述/状态），传入 jobId")
    public String queryJobDetail(Long jobId) {
        if (UserHolder.getUser() == null) {
            return JSONUtil.toJsonStr(Result.fail("用户未登录，无法查询岗位"));
        }
        log.info("[JobTool] 查询岗位详情, jobId={}", jobId);
        if (jobId == null) {
            return JSONUtil.toJsonStr(Result.fail("缺少岗位 id"));
        }
        try {
            return JSONUtil.toJsonStr(jobService.queryById(jobId));
        } catch (Exception e) {
            log.warn("[JobTool] 查询岗位失败, jobId={}", jobId, e);
            return JSONUtil.toJsonStr(Result.fail("岗位查询失败"));
        }
    }
}
