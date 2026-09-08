package com.linggong.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linggong.dto.Result;
import com.linggong.dto.SettlementDetailDTO;
import com.linggong.dto.SettlementItemDTO;
import com.linggong.entity.Attendance;
import com.linggong.entity.Job;
import com.linggong.entity.JobApplication;
import com.linggong.entity.JobSettlement;
import com.linggong.entity.JobSettlementItem;
import com.linggong.entity.User;
import com.linggong.mapper.AttendanceMapper;
import com.linggong.mapper.JobApplicationMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.mapper.JobSettlementItemMapper;
import com.linggong.mapper.JobSettlementMapper;
import com.linggong.mapper.UserMapper;
import com.linggong.service.ISettlementService;
import com.linggong.service.IWalletService;
import com.linggong.utils.AttendancePayUtil;
import com.linggong.utils.CacheClient;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.TaskDaysUtil;
import com.linggong.utils.UserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 岗位结算服务实现。
 *
 * <p>结算口径（与产品决策一致）：
 * <ul>
 *   <li>工资 = 日薪 × 核销半天数 ÷ 2；半天数按考勤终态推导（到岗+下工都过=2、仅到岗过=1、否则 0），
 *       单人封顶任务天数 × 2（保证工资总额 ≤ 冻结额，退款不为负）；</li>
 *   <li>服务费 = 工资总额 × 10%，从雇主<b>可用余额</b>另扣（冻结池只覆盖工资，不含服务费）；</li>
 *   <li>退款 = 冻结额 − 工资总额，退回雇主钱包；</li>
 *   <li>结算后：报名 1→2 已完成、岗位下架、考勤/编辑锁定（已结算岗位不可再核销/编辑）。</li>
 * </ul>
 *
 * <p>幂等：结算单 job_id 唯一，落单即结算完成；重复结算 / 并发触发都被唯一键兜住，
 * 手动提前与到期自动共用同一核心逻辑，最终只成功结算一次。
 */
@Service
public class SettlementServiceImpl implements ISettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementServiceImpl.class);

    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal SERVICE_FEE_RATE = new BigDecimal("0.10");
    /** 每次自动结算扫描的岗位上限 */
    private static final int AUTO_SCAN_LIMIT = 50;

    private final JobMapper jobMapper;
    private final JobApplicationMapper jobApplicationMapper;
    private final AttendanceMapper attendanceMapper;
    private final UserMapper userMapper;
    private final JobSettlementMapper jobSettlementMapper;
    private final JobSettlementItemMapper jobSettlementItemMapper;
    private final IWalletService walletService;
    private final CacheClient cacheClient;
    private final StringRedisTemplate stringRedisTemplate;

    /** 自代理：autoSettleExpired 逐岗结算要走事务，须经代理调用而非 this */
    @Lazy
    @Autowired
    private SettlementServiceImpl self;

    public SettlementServiceImpl(JobMapper jobMapper, JobApplicationMapper jobApplicationMapper,
                                 AttendanceMapper attendanceMapper, UserMapper userMapper,
                                 JobSettlementMapper jobSettlementMapper,
                                 JobSettlementItemMapper jobSettlementItemMapper,
                                 IWalletService walletService, CacheClient cacheClient,
                                 StringRedisTemplate stringRedisTemplate) {
        this.jobMapper = jobMapper;
        this.jobApplicationMapper = jobApplicationMapper;
        this.attendanceMapper = attendanceMapper;
        this.userMapper = userMapper;
        this.jobSettlementMapper = jobSettlementMapper;
        this.jobSettlementItemMapper = jobSettlementItemMapper;
        this.walletService = walletService;
        this.cacheClient = cacheClient;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result detail(Long jobId) {
        Result guard = employerGuard(jobId);
        if (guard != null) {
            return guard;
        }
        Job job = jobMapper.selectById(jobId);
        JobSettlement settlement = jobSettlementMapper.selectByJobId(jobId);
        if (settlement != null) {
            return Result.ok(toDetail(job, itemDTOs(listItems(settlement.getId())), settlement));
        }
        List<JobApplication> hired = hiredApplications(jobId);
        SettlementSummary summary = compute(job, hired);
        return Result.ok(toDetailPreview(job, summary));
    }

    @Override
    public Result settle(Long jobId) {
        Result guard = employerGuard(jobId);
        if (guard != null) {
            return guard;
        }
        return self.settleInternal(jobId, 0);
    }

    /**
     * 结算核心（事务边界）。手动结算与自动结算都经此方法（经自代理调用以启用事务）。
     *
     * @param triggerType 0 手动提前 / 1 到期自动
     */
    @Transactional
    public Result settleInternal(Long jobId, int triggerType) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        if (jobSettlementMapper.selectByJobId(jobId) != null) {
            return Result.fail("该岗位已结算，无需重复操作");
        }
        List<JobApplication> hired = hiredApplications(jobId);
        SettlementSummary summary = compute(job, hired);

        // 服务费余额预检（此时尚未做任何资金动作，失败可安全返回）
        if (summary.serviceFee.signum() > 0
                && walletService.balanceOf(job.getEmployerId()).compareTo(summary.serviceFee) < 0) {
            return Result.fail("可用余额不足以支付服务费 ¥" + money(summary.serviceFee)
                    + "，请先到「我的钱包」充值后再结算");
        }

        // 1. 落结算单（job_id 唯一 = 幂等锚点，并发时由唯一键兜住）
        JobSettlement settlement = new JobSettlement();
        settlement.setJobId(jobId);
        settlement.setEmployerId(job.getEmployerId());
        settlement.setTriggerType(triggerType);
        settlement.setGrossWage(summary.grossWage);
        settlement.setServiceFee(summary.serviceFee);
        settlement.setRefundAmount(summary.refundAmount);
        settlement.setSettledAt(LocalDateTime.now());
        jobSettlementMapper.insert(settlement);
        Long settlementId = settlement.getId();

        // 2. 发工资 + 落逐人明细（从这里起任何失败都抛异常回滚整笔结算）
        String jobName = job.getName();
        for (JobApplication app : hired) {
            int half = summary.halfDaysByWorker.getOrDefault(app.getWorkerId(), 0);
            BigDecimal wage = calcWage(job.getSalary(), half);
            if (wage.signum() > 0) {
                Result r = walletService.settleSalary(app.getWorkerId(), settlementId, wage,
                        "岗位「" + jobName + "」结算工资");
                if (!Boolean.TRUE.equals(r.getSuccess())) {
                    throw new IllegalStateException("结算发工资失败：" + r.getErrorMsg());
                }
            }
            JobSettlementItem item = new JobSettlementItem();
            item.setSettlementId(settlementId);
            item.setApplicationId(app.getId());
            item.setWorkerId(app.getWorkerId());
            item.setPaidHalfDays(half);
            item.setWageAmount(wage);
            jobSettlementItemMapper.insert(item);
        }

        // 3. 扣服务费（雇主可用余额另扣）
        if (summary.serviceFee.signum() > 0) {
            Result r = walletService.chargeServiceFee(job.getEmployerId(), settlementId,
                    summary.serviceFee, "岗位「" + jobName + "」平台服务费（10%）");
            if (!Boolean.TRUE.equals(r.getSuccess())) {
                throw new IllegalStateException("结算扣服务费失败：" + r.getErrorMsg());
            }
        }

        // 4. 退回冻结余款（>0 才退，避免记 0 流水）
        if (summary.refundAmount.signum() > 0) {
            walletService.unfreeze(job.getEmployerId(), settlementId, summary.refundAmount,
                    "岗位「" + jobName + "」结算退回冻结余款");
        }

        // 5. 报名 1→2 已完成
        jobApplicationMapper.finishByJob(jobId);

        // 6. 岗位下架 + 清零冻结额 + 缓存/GEO 同步（结算后不再招聘）
        job.setStatus(1);
        job.setFrozenAmount(BigDecimal.ZERO);
        jobMapper.updateById(job);
        cacheClient.delete(RedisConstants.CACHE_JOB_KEY + jobId);
        removeJobGeo(job.getCategoryId(), jobId);

        return Result.ok(toDetail(job, summary.items, settlement));
    }

    @Override
    public void autoSettleExpired() {
        List<Job> expired = jobSettlementMapper.selectExpiredUnsettledJobs(AUTO_SCAN_LIMIT);
        if (expired.isEmpty()) {
            return;
        }
        for (Job job : expired) {
            try {
                Result r = self.settleInternal(job.getId(), 1);
                if (Boolean.TRUE.equals(r.getSuccess())) {
                    log.info("岗位 {} 到期自动结算成功", job.getId());
                } else {
                    // 余额不足等可恢复失败：保持未结算，下次任务重试
                    log.warn("岗位 {} 到期自动结算未完成：{}", job.getId(), r.getErrorMsg());
                }
            } catch (Exception e) {
                log.warn("岗位 {} 到期自动结算异常：{}", job.getId(), e.getMessage());
            }
        }
    }

    // ---------- 计算与组装 ----------

    /** 结算金额中间结果。 */
    private static final class SettlementSummary {
        Map<Long, Integer> halfDaysByWorker;
        List<SettlementItemDTO> items;
        BigDecimal grossWage;
        BigDecimal serviceFee;
        BigDecimal refundAmount;
    }

    private SettlementSummary compute(Job job, List<JobApplication> hired) {
        SettlementSummary s = new SettlementSummary();
        s.items = new ArrayList<>();
        s.grossWage = BigDecimal.ZERO;

        // 该岗位全部考勤，按工人累计核销半天数
        Map<Long, Integer> halfDaysByWorker = new HashMap<>();
        if (!hired.isEmpty()) {
            List<Long> workerIds = hired.stream().map(JobApplication::getWorkerId)
                    .distinct().collect(Collectors.toList());
            int maxHalf = TaskDaysUtil.taskDays(job) * 2;
            attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                            .eq(Attendance::getJobId, job.getId())
                            .in(Attendance::getWorkerId, workerIds))
                    .forEach(a -> {
                        int h = AttendancePayUtil.halfDays(a);
                        if (h > 0) {
                            halfDaysByWorker.merge(a.getWorkerId(), h, Integer::sum);
                        }
                    });
            // 单人封顶：最多任务天数天（任务期内每天至多 2 半天）
            halfDaysByWorker.replaceAll((w, v) -> Math.min(v, maxHalf));
        }
        s.halfDaysByWorker = halfDaysByWorker;

        BigDecimal salary = job.getSalary() == null ? BigDecimal.ZERO : BigDecimal.valueOf(job.getSalary());
        for (JobApplication app : hired) {
            int half = halfDaysByWorker.getOrDefault(app.getWorkerId(), 0);
            BigDecimal wage = calcWage(job.getSalary(), half);
            s.grossWage = s.grossWage.add(wage);
            SettlementItemDTO item = new SettlementItemDTO();
            item.setWorkerId(app.getWorkerId());
            item.setHalfDays(half);
            item.setWageAmount(wage);
            s.items.add(item);
        }
        s.serviceFee = s.grossWage.multiply(SERVICE_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal frozen = job.getFrozenAmount() == null ? BigDecimal.ZERO : job.getFrozenAmount();
        BigDecimal refund = frozen.subtract(s.grossWage);
        s.refundAmount = refund.signum() < 0 ? BigDecimal.ZERO : refund;
        return s;
    }

    private BigDecimal calcWage(Integer salary, int halfDays) {
        if (salary == null || salary <= 0 || halfDays <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(salary)
                .multiply(BigDecimal.valueOf(halfDays))
                .divide(TWO, 2, RoundingMode.HALF_UP);
    }

    private List<JobApplication> hiredApplications(Long jobId) {
        return jobApplicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getJobId, jobId)
                .eq(JobApplication::getStatus, 1));
    }

    private List<JobSettlementItem> listItems(Long settlementId) {
        return jobSettlementItemMapper.selectList(new LambdaQueryWrapper<JobSettlementItem>()
                .eq(JobSettlementItem::getSettlementId, settlementId)
                .orderByAsc(JobSettlementItem::getId));
    }

    private List<SettlementItemDTO> itemDTOs(List<JobSettlementItem> items) {
        return items.stream().map(i -> {
            SettlementItemDTO d = new SettlementItemDTO();
            d.setWorkerId(i.getWorkerId());
            d.setHalfDays(i.getPaidHalfDays());
            d.setWageAmount(i.getWageAmount());
            return d;
        }).collect(Collectors.toList());
    }

    /** 未结算预览：金额来自实时重算，settled 为 false。 */
    private SettlementDetailDTO toDetailPreview(Job job, SettlementSummary summary) {
        SettlementDetailDTO dto = toDetail(job, summary.items, null);
        dto.setGrossWage(summary.grossWage);
        dto.setServiceFee(summary.serviceFee);
        dto.setRefundAmount(summary.refundAmount);
        return dto;
    }

    private SettlementDetailDTO toDetail(Job job, List<SettlementItemDTO> items, JobSettlement settlement) {
        SettlementDetailDTO dto = new SettlementDetailDTO();
        dto.setJobId(job.getId());
        dto.setJobName(job.getName());
        dto.setSalary(job.getSalary());
        dto.setFrozenAmount(job.getFrozenAmount());
        dto.setHiredCount(items == null ? 0 : items.size());
        dto.setSettled(settlement != null);
        if (settlement != null) {
            dto.setTriggerType(settlement.getTriggerType());
            dto.setGrossWage(settlement.getGrossWage());
            dto.setServiceFee(settlement.getServiceFee());
            dto.setRefundAmount(settlement.getRefundAmount());
            dto.setSettleTime(settlement.getSettledAt() == null ? null : settlement.getSettledAt().toString());
        } else {
            dto.setGrossWage(BigDecimal.ZERO);
            dto.setServiceFee(BigDecimal.ZERO);
            dto.setRefundAmount(BigDecimal.ZERO);
        }
        // 填工人昵称/头像
        if (items != null && !items.isEmpty()) {
            List<Long> workerIds = items.stream().map(SettlementItemDTO::getWorkerId)
                    .distinct().collect(Collectors.toList());
            Map<Long, User> userMap = userMapper.selectBatchIds(workerIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
            items.forEach(item -> {
                User worker = userMap.get(item.getWorkerId());
                if (worker != null) {
                    item.setWorkerName(worker.getNickName());
                    item.setWorkerIcon(worker.getIcon());
                }
            });
        }
        dto.setItems(items == null ? Collections.emptyList() : items);
        return dto;
    }

    /** 雇主校验：登录 + 岗位归属。返回 null 表示通过，否则为失败 Result。 */
    private Result employerGuard(Long jobId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Integer role = UserHolder.getUser().getRole();
        if (role == null || role != 1) {
            return Result.fail("只有雇主可以结算岗位");
        }
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        if (!job.getEmployerId().equals(UserHolder.getUser().getId())) {
            return Result.fail("只能结算自己发布的岗位");
        }
        return null;
    }

    private void removeJobGeo(Long categoryId, Long jobId) {
        if (categoryId == null) {
            return;
        }
        stringRedisTemplate.opsForGeo().remove(RedisConstants.GEO_JOB_KEY + categoryId, String.valueOf(jobId));
    }

    private String money(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
