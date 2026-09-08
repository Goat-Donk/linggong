package com.linggong.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.AttendanceAuditDTO;
import com.linggong.dto.AttendanceConfirmDTO;
import com.linggong.dto.AttendanceDayDTO;
import com.linggong.dto.AttendanceJobDayDTO;
import com.linggong.dto.AttendanceJobDTO;
import com.linggong.dto.AttendanceMyDTO;
import com.linggong.dto.Result;
import com.linggong.entity.Attendance;
import com.linggong.entity.Job;
import com.linggong.entity.JobApplication;
import com.linggong.entity.User;
import com.linggong.mapper.AttendanceMapper;
import com.linggong.mapper.JobApplicationMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.mapper.JobSettlementMapper;
import com.linggong.mapper.UserMapper;
import com.linggong.service.IAttendanceService;
import com.linggong.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 每日考勤服务实现。
 *
 * <p>考勤状态机（一天两次打卡申请，雇主各自核销）：
 * <pre>
 *   到岗 on_status  0 未申请 →（打工人「我已到岗」）1 待核销 →（通过）2 /（驳回）3
 *   下工 off_status 0 未申请 →（打工人「我请求下工」，需 on 已通过）1 待核销 →（通过）2 /（驳回）3
 *   雇主「补记今日完工」：直接置 on=2、off=2（工人到了但忘了申请下工）
 * </pre>
 * 计薪推导（结算时按核销终态）：on=2 且 off=2 → 1 天；on=2 且 off 未通过 → 0.5 天；否则 0。
 *
 * <p>角色边界：打卡/我的考勤仅打工人（role=0）；岗位列表/按日核销/补记仅雇主（role=1）。
 */
@Service
public class AttendanceServiceImpl extends ServiceImpl<AttendanceMapper, Attendance>
        implements IAttendanceService {

    private static final int ON_PENDING = 1;
    private static final int ON_PASS = 2;
    private static final int ON_REJECT = 3;
    private static final int OFF_PENDING = 1;
    private static final int OFF_PASS = 2;
    private static final int OFF_REJECT = 3;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JobMapper jobMapper;
    private final JobApplicationMapper jobApplicationMapper;
    private final JobSettlementMapper jobSettlementMapper;
    private final UserMapper userMapper;

    public AttendanceServiceImpl(JobMapper jobMapper,
                                 JobApplicationMapper jobApplicationMapper,
                                 JobSettlementMapper jobSettlementMapper,
                                 UserMapper userMapper) {
        this.jobMapper = jobMapper;
        this.jobApplicationMapper = jobApplicationMapper;
        this.jobSettlementMapper = jobSettlementMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public Result punchOn(Long jobId) {
        Result roleCheck = workerOnly();
        if (roleCheck != null) {
            return roleCheck;
        }
        Job job = requireJob(jobId);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        if (isSettled(jobId)) {
            return Result.fail("该岗位已结算，考勤已锁定");
        }
        Long workerId = UserHolder.getUser().getId();
        LocalDate today = LocalDate.now();
        if (!inPeriod(job, today)) {
            return Result.fail("今天不在任务期内，无法打卡");
        }
        if (!isHired(jobId, workerId)) {
            return Result.fail("需先被雇主录用才能打卡");
        }
        Attendance row = findByDate(jobId, workerId, today);
        if (row != null) {
            if (ON_PASS == row.getOnStatus()) {
                return Result.fail("今日到岗已通过，无需重复申请");
            }
            if (ON_REJECT == row.getOnStatus()) {
                return Result.fail("今日到岗已被驳回");
            }
            // 待核销中再次点击 = 幂等，仅刷新申请时间
            row.setOnTime(LocalDateTime.now());
            updateById(row);
            return Result.ok(toDayDTO(row, workerId, today));
        }
        row = new Attendance();
        row.setJobId(jobId);
        row.setWorkerId(workerId);
        row.setWorkDate(today);
        row.setOnStatus(ON_PENDING);
        row.setOnTime(LocalDateTime.now());
        row.setOffStatus(0);
        save(row);
        return Result.ok(toDayDTO(row, workerId, today));
    }

    @Override
    @Transactional
    public Result punchOff(Long jobId) {
        Result roleCheck = workerOnly();
        if (roleCheck != null) {
            return roleCheck;
        }
        Job job = requireJob(jobId);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        if (isSettled(jobId)) {
            return Result.fail("该岗位已结算，考勤已锁定");
        }
        Long workerId = UserHolder.getUser().getId();
        LocalDate today = LocalDate.now();
        if (!inPeriod(job, today)) {
            return Result.fail("今天不在任务期内，无法打卡");
        }
        if (!isHired(jobId, workerId)) {
            return Result.fail("需先被雇主录用才能打卡");
        }
        Attendance row = findByDate(jobId, workerId, today);
        if (row == null || ON_PASS != row.getOnStatus()) {
            return Result.fail("今日到岗尚未通过，不能申请下工");
        }
        if (OFF_PASS == row.getOffStatus()) {
            return Result.fail("今日下工已通过");
        }
        if (OFF_REJECT == row.getOffStatus()) {
            return Result.fail("今日下工已被驳回，不能重复申请");
        }
        row.setOffStatus(OFF_PENDING);
        row.setOffTime(LocalDateTime.now());
        updateById(row);
        return Result.ok(toDayDTO(row, workerId, today));
    }

    @Override
    public Result myAttendance(Long jobId) {
        // 我的考勤不限角色：有报名记录的本人查看（打工人主入口，雇主发布自己的岗不会走到这）
        Job job = requireJob(jobId);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        Long workerId = UserHolder.getUser().getId();
        LocalDate today = LocalDate.now();
        boolean hired = isHired(jobId, workerId);
        AttendanceMyDTO view = new AttendanceMyDTO();
        view.setJobId(jobId);
        view.setJobName(job.getName());
        view.setSalary(job.getSalary());
        view.setStartTime(str(job.getStartTime()));
        view.setEndTime(str(job.getEndTime()));
        view.setToday(today.format(DAY));
        boolean period = inPeriod(job, today);
        view.setInPeriod(period);
        view.setHired(hired);

        Attendance todayRow = findByDate(jobId, workerId, today);
        view.setTodayRow(toDayDTO(todayRow, workerId, today));
        // 到岗可点：已录用 + 今天在任务期内，且到岗未到终态（0 未申请 或 1 待核销中可重复提交）；
        // 下工可点：已录用 + 到岗已通过，且下工未到终态
        boolean onCan = todayRow == null || todayRow.getOnStatus() == 0 || todayRow.getOnStatus() == ON_PENDING;
        boolean onPassed = todayRow != null && todayRow.getOnStatus() == ON_PASS;
        boolean offCan = onPassed && (todayRow.getOffStatus() == 0 || todayRow.getOffStatus() == OFF_PENDING);
        view.setCanOn(hired && period && onCan);
        view.setCanOff(hired && period && offCan);
        view.setHint(buildTodayHint(job, todayRow, hired));

        // 历史打卡记录（不含今天，今天单独展示）：按日期升序；未打卡的天缺勤无记录
        List<Attendance> records = lambdaQuery()
                .eq(Attendance::getJobId, jobId)
                .eq(Attendance::getWorkerId, workerId)
                .lt(Attendance::getWorkDate, today)
                .orderByAsc(Attendance::getWorkDate)
                .list();
        view.setRecords(records.stream()
                .map(row -> toDayDTO(row, workerId, today))
                .collect(Collectors.toList()));
        return Result.ok(view);
    }

    @Override
    public Result myAttendanceJobs() {
        Result roleCheck = employerOnly();
        if (roleCheck != null) {
            return roleCheck;
        }
        Long employerId = UserHolder.getUser().getId();
        List<Job> myJobs = jobMapper.selectList(new LambdaQueryWrapper<Job>()
                .eq(Job::getEmployerId, employerId));
        if (myJobs.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> jobIds = myJobs.stream().map(Job::getId).collect(Collectors.toList());
        // 每个岗位已录用（可打卡）人数
        Map<Long, Long> hiredCount = jobApplicationMapper.selectList(
                        new LambdaQueryWrapper<JobApplication>()
                                .in(JobApplication::getJobId, jobIds)
                                .eq(JobApplication::getStatus, 1))
                .stream().collect(Collectors.groupingBy(JobApplication::getJobId, Collectors.counting()));
        List<AttendanceJobDTO> list = myJobs.stream()
                .filter(job -> hiredCount.getOrDefault(job.getId(), 0L) > 0)
                .map(job -> {
                    AttendanceJobDTO dto = new AttendanceJobDTO();
                    dto.setId(job.getId());
                    dto.setName(job.getName());
                    dto.setSalary(job.getSalary());
                    dto.setStartTime(str(job.getStartTime()));
                    dto.setEndTime(str(job.getEndTime()));
                    dto.setHiredCount(hiredCount.get(job.getId()).intValue());
                    return dto;
                })
                .collect(Collectors.toList());
        return Result.ok(list);
    }

    @Override
    public Result jobAttendance(Long jobId, String date) {
        Result roleCheck = employerOnly();
        if (roleCheck != null) {
            return roleCheck;
        }
        Job job = requireJob(jobId);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        if (!job.getEmployerId().equals(UserHolder.getUser().getId())) {
            return Result.fail("只能核销自己发布岗位的考勤");
        }
        LocalDate parsedDay = parseDay(date);
        LocalDate day = parsedDay == null ? LocalDate.now() : parsedDay; // 未传/非法日期默认今天
        AttendanceJobDayDTO view = new AttendanceJobDayDTO();
        view.setJobId(jobId);
        view.setJobName(job.getName());
        view.setSalary(job.getSalary());
        view.setStartTime(str(job.getStartTime()));
        view.setEndTime(str(job.getEndTime()));
        view.setDate(day.format(DAY));
        view.setInPeriod(inPeriod(job, day));

        // 已录用工人
        List<JobApplication> hired = jobApplicationMapper.selectList(
                new LambdaQueryWrapper<JobApplication>()
                        .eq(JobApplication::getJobId, jobId)
                        .eq(JobApplication::getStatus, 1));
        if (hired.isEmpty()) {
            view.setRows(Collections.emptyList());
            return Result.ok(view);
        }
        List<Long> workerIds = hired.stream().map(JobApplication::getWorkerId).collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(workerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        // 该日各工人打卡记录（无打卡的工人补空行占位）
        Map<Long, Attendance> byWorker = lambdaQuery()
                .eq(Attendance::getJobId, jobId)
                .eq(Attendance::getWorkDate, day)
                .in(Attendance::getWorkerId, workerIds)
                .list().stream()
                .collect(Collectors.toMap(Attendance::getWorkerId, Function.identity()));
        List<AttendanceDayDTO> rows = hired.stream().map(app -> {
            User worker = userMap.get(app.getWorkerId());
            Attendance row = byWorker.get(app.getWorkerId());
            AttendanceDayDTO dto = toDayDTO(row, app.getWorkerId(), day);
            if (worker != null) {
                dto.setWorkerName(worker.getNickName());
                dto.setWorkerIcon(worker.getIcon());
            }
            return dto;
        }).collect(Collectors.toList());
        view.setRows(rows);
        return Result.ok(view);
    }

    @Override
    @Transactional
    public Result audit(AttendanceAuditDTO dto) {
        Result roleCheck = employerOnly();
        if (roleCheck != null) {
            return roleCheck;
        }
        if (dto == null || dto.getJobId() == null || dto.getWorkerId() == null
                || dto.getWorkDate() == null || dto.getPass() == null) {
            return Result.fail("参数不完整");
        }
        Job job = requireJob(dto.getJobId());
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        if (!job.getEmployerId().equals(UserHolder.getUser().getId())) {
            return Result.fail("只能核销自己发布岗位的考勤");
        }
        if (isSettled(dto.getJobId())) {
            return Result.fail("该岗位已结算，考勤已锁定");
        }
        LocalDate day = parseDay(dto.getWorkDate());
        if (day == null) {
            return Result.fail("日期格式不正确，应为 yyyy-MM-dd");
        }
        String punch = dto.getPunch();
        if (!"on".equals(punch) && !"off".equals(punch)) {
            return Result.fail("punch 只能是 on(到岗) 或 off(下工)");
        }
        Attendance row = findByDate(dto.getJobId(), dto.getWorkerId(), day);
        if (row == null) {
            return Result.fail("该工人当天没有打卡申请");
        }
        boolean pass = Boolean.TRUE.equals(dto.getPass());
        if ("on".equals(punch)) {
            if (ON_PENDING != row.getOnStatus()) {
                return Result.fail("该到岗申请已处理，不能重复核销");
            }
            row.setOnStatus(pass ? ON_PASS : ON_REJECT);
        } else {
            if (ON_PASS != row.getOnStatus()) {
                return Result.fail("该工人到岗未通过，不能核销下工");
            }
            if (OFF_PENDING != row.getOffStatus()) {
                return Result.fail("该下工申请已处理，不能重复核销");
            }
            row.setOffStatus(pass ? OFF_PASS : OFF_REJECT);
        }
        updateById(row);
        return Result.ok(toDayDTO(row, row.getWorkerId(), day));
    }

    @Override
    @Transactional
    public Result confirmOff(AttendanceConfirmDTO dto) {
        Result roleCheck = employerOnly();
        if (roleCheck != null) {
            return roleCheck;
        }
        if (dto == null || dto.getJobId() == null || dto.getWorkerId() == null) {
            return Result.fail("参数不完整");
        }
        Job job = requireJob(dto.getJobId());
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        if (!job.getEmployerId().equals(UserHolder.getUser().getId())) {
            return Result.fail("只能核销自己发布岗位的考勤");
        }
        if (isSettled(dto.getJobId())) {
            return Result.fail("该岗位已结算，考勤已锁定");
        }
        LocalDate day = parseDay(dto.getWorkDate());
        if (day == null) {
            day = LocalDate.now();
        }
        if (!day.equals(LocalDate.now())) {
            return Result.fail("补记仅限今日，历史日期请逐日核销");
        }
        if (!inPeriod(job, day)) {
            return Result.fail("今天不在任务期内，无法补记完工");
        }
        Attendance row = findByDate(dto.getJobId(), dto.getWorkerId(), day);
        if (row == null) {
            row = new Attendance();
            row.setJobId(dto.getJobId());
            row.setWorkerId(dto.getWorkerId());
            row.setWorkDate(day);
            row.setOnStatus(ON_PASS);
            row.setOffStatus(OFF_PASS);
            row.setOnTime(LocalDateTime.now());
            row.setOffTime(LocalDateTime.now());
            save(row);
            return Result.ok(toDayDTO(row, row.getWorkerId(), day));
        }
        if (ON_REJECT == row.getOnStatus()) {
            return Result.fail("该工人今日到岗已被驳回，不能补记完工");
        }
        row.setOnStatus(ON_PASS);
        row.setOffStatus(OFF_PASS);
        row.setOffTime(LocalDateTime.now());
        if (row.getOnTime() == null) {
            row.setOnTime(LocalDateTime.now());
        }
        updateById(row);
        return Result.ok(toDayDTO(row, row.getWorkerId(), day));
    }

    // ---------- 私有辅助 ----------

    /** 仅打工人（role=0）。返回 null 表示通过，否则为失败 Result。 */
    private Result workerOnly() {
        Integer role = UserHolder.getUser().getRole();
        return role != null && role == 0 ? null : Result.fail("只有打工人可以打卡");
    }

    /** 仅雇主（role=1）。返回 null 表示通过，否则为失败 Result。 */
    private Result employerOnly() {
        Integer role = UserHolder.getUser().getRole();
        return role != null && role == 1 ? null : Result.fail("只有雇主可以核销考勤");
    }

    private Job requireJob(Long jobId) {
        return jobId == null ? null : jobMapper.selectById(jobId);
    }

    /** 岗位是否已结算（结算后考勤不可再打卡/核销/补记）。 */
    private boolean isSettled(Long jobId) {
        return jobSettlementMapper.selectByJobId(jobId) != null;
    }

    private boolean isHired(Long jobId, Long workerId) {
        Long count = jobApplicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getJobId, jobId)
                .eq(JobApplication::getWorkerId, workerId)
                .eq(JobApplication::getStatus, 1));
        return count != null && count > 0;
    }

    /** 是否在岗位任务期内（无起止的单日任务按任意日期放行）。 */
    private boolean inPeriod(Job job, LocalDate day) {
        if (job.getStartTime() != null && day.isBefore(job.getStartTime().toLocalDate())) {
            return false;
        }
        return job.getEndTime() == null || !day.isAfter(job.getEndTime().toLocalDate());
    }

    private Attendance findByDate(Long jobId, Long workerId, LocalDate date) {
        return lambdaQuery()
                .eq(Attendance::getJobId, jobId)
                .eq(Attendance::getWorkerId, workerId)
                .eq(Attendance::getWorkDate, date)
                .one();
    }

    /**
     * 考勤记录 → DTO。refDate 是所代表的那一天（工人「今天」/ 雇主所选日期）；
     * row 为 null 表示该日无打卡（未到岗），返回占位行。
     */
    private AttendanceDayDTO toDayDTO(Attendance row, Long workerId, LocalDate refDate) {
        AttendanceDayDTO dto = new AttendanceDayDTO();
        dto.setWorkerId(row == null ? workerId : row.getWorkerId());
        dto.setWorkDate(refDate.format(DAY));
        if (row == null) {
            dto.setOnStatus(0);
            dto.setOffStatus(0);
            dto.setToday(refDate.equals(LocalDate.now()));
            dto.setDayText("未到岗");
            dto.setDayValue(0.0);
            return dto;
        }
        dto.setOnStatus(row.getOnStatus());
        dto.setOffStatus(row.getOffStatus());
        dto.setOnTime(row.getOnTime() == null ? null : row.getOnTime().toString());
        dto.setOffTime(row.getOffTime() == null ? null : row.getOffTime().toString());
        dto.setToday(row.getWorkDate().equals(LocalDate.now()));
        fillDayText(dto);
        return dto;
    }

    /**
     * 按 on/off 终态推导文案与展示计薪天数。
     * 核心规则：on+off 都通过=1 天；仅 on 通过=0.5 天；否则 0。
     */
    private void fillDayText(AttendanceDayDTO dto) {
        int on = dto.getOnStatus() == null ? 0 : dto.getOnStatus();
        int off = dto.getOffStatus() == null ? 0 : dto.getOffStatus();
        if (on == ON_PASS && off == OFF_PASS) {
            dto.setDayText("满勤（1 天）");
            dto.setDayValue(1.0);
        } else if (on == ON_PASS) {
            dto.setDayText(off == OFF_PENDING ? "在岗 · 下工待核销"
                    : off == OFF_REJECT ? "早退（半天）" : "在岗 · 未申请下工");
            dto.setDayValue(0.5);
        } else if (on == ON_PENDING) {
            dto.setDayText("到岗待核销");
            dto.setDayValue(0.0);
        } else if (on == ON_REJECT) {
            dto.setDayText("到岗被驳回");
            dto.setDayValue(0.0);
        } else {
            dto.setDayText("未到岗");
            dto.setDayValue(0.0);
        }
    }

    /** 今天操作提示文案。 */
    private String buildTodayHint(Job job, Attendance row, boolean hired) {
        if (!hired) {
            return "尚未被该岗位录用，无法打卡";
        }
        if (!inPeriod(job, LocalDate.now())) {
            return "今天不在任务期内，无需打卡";
        }
        if (row == null) {
            return "今天还未打卡";
        }
        if (ON_REJECT == row.getOnStatus()) {
            return "今日到岗被驳回，请与雇主沟通";
        }
        if (ON_PENDING == row.getOnStatus()) {
            return "到岗申请已提交，待雇主核销";
        }
        if (OFF_PASS == row.getOffStatus()) {
            return "今日考勤已完成（满勤 1 天）";
        }
        if (OFF_PENDING == row.getOffStatus()) {
            return "下工申请已提交，待雇主核销";
        }
        return "已到岗，可申请下工";
    }

    private String str(LocalDateTime time) {
        return time == null ? null : time.toString();
    }

    private LocalDate parseDay(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date, DAY);
        } catch (Exception e) {
            return null;
        }
    }
}
