package com.linggong.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.EvaluationDTO;
import com.linggong.dto.EvaluationFormDTO;
import com.linggong.dto.Result;
import com.linggong.entity.Job;
import com.linggong.entity.JobApplication;
import com.linggong.entity.JobEvaluation;
import com.linggong.entity.User;
import com.linggong.mapper.JobApplicationMapper;
import com.linggong.mapper.JobEvaluationMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.mapper.UserMapper;
import com.linggong.service.IJobEvaluationService;
import com.linggong.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 互评服务实现。
 *
 * <p>发布规则：
 * <ul>
 *   <li>评价人必须是当前登录用户；</li>
 *   <li>评价人与被评价人必须是该岗位的雇佣双方（一方雇主、一方报名工人），且不能评自己；</li>
 *   <li>同一岗位同一评价人只能评一次。</li>
 * </ul>
 */
@Service
public class JobEvaluationServiceImpl extends ServiceImpl<JobEvaluationMapper, JobEvaluation>
        implements IJobEvaluationService {

    private final JobMapper jobMapper;
    private final JobApplicationMapper jobApplicationMapper;
    private final UserMapper userMapper;

    public JobEvaluationServiceImpl(JobMapper jobMapper,
                                    JobApplicationMapper jobApplicationMapper,
                                    UserMapper userMapper) {
        this.jobMapper = jobMapper;
        this.jobApplicationMapper = jobApplicationMapper;
        this.userMapper = userMapper;
    }

    @Override
    public Result publish(EvaluationFormDTO form) {
        Long fromUserId = UserHolder.getUser().getId();
        Long toUserId = form.getToUserId();
        Long jobId = form.getJobId();

        // 1. 岗位存在性校验
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        // 2. 不能评价自己
        if (fromUserId.equals(toUserId)) {
            return Result.fail("不能评价自己");
        }

        // 3. 校验雇佣关系：from/to 必须一方是雇主、一方是报名工人
        Long employerId = job.getEmployerId();
        Long workerId;
        if (fromUserId.equals(employerId)) {
            // 雇主评工人：被评价人必须是报名工人
            workerId = toUserId;
        } else if (toUserId.equals(employerId)) {
            // 工人评雇主：评价人必须是报名工人
            workerId = fromUserId;
        } else {
            return Result.fail("只能评价该岗位的雇佣对方");
        }
        // 互评资格：报名须已「已完成」(status=2)，即该岗位已结算，雇佣闭环成立后才可互评
        Long applyCount = jobApplicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getJobId, jobId)
                .eq(JobApplication::getWorkerId, workerId)
                .eq(JobApplication::getStatus, 2));
        if (applyCount == null || applyCount == 0) {
            return Result.fail("该岗位尚未结算，暂不能评价");
        }

        // 4. 防重复评价：同一岗位同一评价人只评一次
        Long evaluated = baseMapper.selectCount(new LambdaQueryWrapper<JobEvaluation>()
                .eq(JobEvaluation::getJobId, jobId)
                .eq(JobEvaluation::getFromUserId, fromUserId));
        if (evaluated != null && evaluated > 0) {
            return Result.fail("您已评价过该岗位");
        }

        // 5. 落库
        JobEvaluation evaluation = new JobEvaluation();
        evaluation.setJobId(jobId);
        evaluation.setFromUserId(fromUserId);
        evaluation.setToUserId(toUserId);
        evaluation.setRating(form.getRating());
        evaluation.setContent(form.getContent());
        save(evaluation);
        return Result.ok(evaluation.getId());
    }

    @Override
    public Result queryByJob(Long jobId, Integer page, Integer pageSize) {
        Page<JobEvaluation> result = lambdaQuery()
                .eq(JobEvaluation::getJobId, jobId)
                .orderByDesc(JobEvaluation::getCreateTime)
                .page(new Page<>(page, pageSize));

        List<JobEvaluation> records = result.getRecords();
        if (records.isEmpty()) {
            return Result.ok(Collections.emptyList(), result.getTotal());
        }

        // 批量查评价人/被评价人信息，避免 N+1
        Set<Long> userIds = records.stream()
                .flatMap(e -> Stream.of(e.getFromUserId(), e.getToUserId()))
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<EvaluationDTO> dtos = records.stream().map(e -> {
            EvaluationDTO dto = BeanUtil.copyProperties(e, EvaluationDTO.class);
            User from = userMap.get(e.getFromUserId());
            User to = userMap.get(e.getToUserId());
            if (from != null) {
                dto.setFromNickName(from.getNickName());
                dto.setFromIcon(from.getIcon());
            }
            if (to != null) {
                dto.setToNickName(to.getNickName());
                dto.setToIcon(to.getIcon());
            }
            return dto;
        }).collect(Collectors.toList());

        return Result.ok(dtos, result.getTotal());
    }
}
