package com.linggong.tools;

import cn.hutool.json.JSONUtil;
import com.linggong.dto.Result;
import com.linggong.service.IAttendanceService;
import com.linggong.utils.UserHolder;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 工具：我的考勤查询。
 *
 * <p>复用 IAttendanceService.myAttendanceJobs（当前登录打工人各岗位的考勤汇总），只读。
 */
@Slf4j
@Component
public class AttendanceTool {

    private final IAttendanceService attendanceService;

    public AttendanceTool(IAttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @Tool("查询当前登录打工人（role=0）的考勤记录（各岗位的到岗/下工核销状态汇总）")
    public String queryMyAttendances() {
        Long userId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        log.info("[AttendanceTool] 查询我的考勤, userId={}", userId);
        if (userId == null) {
            return JSONUtil.toJsonStr(Result.fail("用户未登录，无法查询考勤"));
        }
        try {
            return JSONUtil.toJsonStr(attendanceService.myAttendanceJobs());
        } catch (Exception e) {
            log.warn("[AttendanceTool] 查询考勤失败, userId={}", userId, e);
            return JSONUtil.toJsonStr(Result.fail("考勤查询失败"));
        }
    }
}
