package com.linggong.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.BlacklistFormDTO;
import com.linggong.dto.EmployerBlacklistDTO;
import com.linggong.dto.Result;
import com.linggong.dto.UserDTO;
import com.linggong.entity.EmployerBlacklist;
import com.linggong.entity.User;
import com.linggong.mapper.EmployerBlacklistMapper;
import com.linggong.mapper.UserMapper;
import com.linggong.service.IEmployerBlacklistService;
import com.linggong.service.IJobApplicationService;
import com.linggong.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 雇主拉黑打工人服务实现。
 *
 * <p>全局生效 + 防滥用上限 + 可解除 + 静默，语义见 {@link IEmployerBlacklistService}。
 * 拉黑侧效（取消该工人待确认报名）复用了报名服务的名额释放逻辑，保证名额一致。
 */
@Service
public class EmployerBlacklistServiceImpl extends ServiceImpl<EmployerBlacklistMapper, EmployerBlacklist>
        implements IEmployerBlacklistService {

    private final UserMapper userMapper;
    private final IJobApplicationService jobApplicationService;

    public EmployerBlacklistServiceImpl(UserMapper userMapper, IJobApplicationService jobApplicationService) {
        this.userMapper = userMapper;
        this.jobApplicationService = jobApplicationService;
    }

    @Override
    @Transactional
    public Result add(BlacklistFormDTO form) {
        // 1. 角色边界：只有雇主能拉黑
        UserDTO user = UserHolder.getUser();
        if (user.getRole() == null || user.getRole() != 1) {
            return Result.fail("只有雇主可以拉黑打工人");
        }
        Long employerId = user.getId();
        Long workerId = form.getWorkerId();
        if (workerId == null) {
            return Result.fail("请选择要拉黑的打工人");
        }

        // 2. 目标校验：被打工人必须真实存在且是打工人（雇主不能被拉黑）
        User worker = userMapper.selectById(workerId);
        if (worker == null) {
            return Result.fail("打工人不存在");
        }
        if (worker.getRole() == null || worker.getRole() != 0) {
            return Result.fail("只能拉黑打工人，不能拉黑雇主");
        }

        // 3. 重复校验
        if (count(new LambdaQueryWrapper<EmployerBlacklist>()
                .eq(EmployerBlacklist::getEmployerId, employerId)
                .eq(EmployerBlacklist::getWorkerId, workerId)) > 0) {
            return Result.fail("该打工人已在你的黑名单中");
        }

        // 4. 防滥用上限：同时拉黑人数封顶，需先解除再拉新（避免雇主把潜在报名者全拉黑）
        long activeCount = count(new LambdaQueryWrapper<EmployerBlacklist>()
                .eq(EmployerBlacklist::getEmployerId, employerId));
        if (activeCount >= IEmployerBlacklistService.MAX_ACTIVE_BLACKLIST) {
            return Result.fail("黑名单已达上限（最多 " + IEmployerBlacklistService.MAX_ACTIVE_BLACKLIST
                    + " 人），请先解除部分再拉黑");
        }

        // 5. 原因清洗（≤100 字）
        String reason = form.getReason();
        if (reason != null) {
            reason = reason.trim();
            if (reason.length() > 100) {
                return Result.fail("拉黑原因最多 100 字");
            }
        }

        // 6. 落库（唯一键兜底并发重复）
        EmployerBlacklist blacklist = new EmployerBlacklist();
        blacklist.setEmployerId(employerId);
        blacklist.setWorkerId(workerId);
        blacklist.setReason(reason == null ? "" : reason);
        try {
            save(blacklist);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return Result.fail("该打工人已在你的黑名单中");
        }

        // 7. 侧效：自动取消该工人对本雇主所有岗位的「待确认」报名并释放名额，
        //    避免「已被拉黑却还能被审核」的矛盾；已录用/已核销不打断（可履约完）。静默不通知。
        jobApplicationService.cancelPendingOfWorkerOnEmployer(employerId, workerId);
        return Result.ok();
    }

    @Override
    public Result remove(Long workerId) {
        UserDTO user = UserHolder.getUser();
        if (user.getRole() == null || user.getRole() != 1) {
            return Result.fail("只有雇主可以解除拉黑");
        }
        boolean removed = remove(new LambdaQueryWrapper<EmployerBlacklist>()
                .eq(EmployerBlacklist::getEmployerId, user.getId())
                .eq(EmployerBlacklist::getWorkerId, workerId));
        if (!removed) {
            return Result.fail("该打工人不在你的黑名单中");
        }
        return Result.ok();
    }

    @Override
    public Result myList(Integer page, Integer pageSize) {
        UserDTO user = UserHolder.getUser();
        if (user.getRole() == null || user.getRole() != 1) {
            return Result.fail("只有雇主可以查看黑名单");
        }
        Long employerId = user.getId();
        Page<EmployerBlacklist> pageResult = lambdaQuery()
                .eq(EmployerBlacklist::getEmployerId, employerId)
                .orderByDesc(EmployerBlacklist::getCreateTime)
                .orderByDesc(EmployerBlacklist::getId)
                .page(new Page<>(page, pageSize));
        List<EmployerBlacklist> records = pageResult.getRecords();
        if (records.isEmpty()) {
            return Result.ok(Collections.emptyList(), pageResult.getTotal());
        }
        // 批量取被打工人昵称/头像，避免 N+1
        List<Long> workerIds = records.stream()
                .map(EmployerBlacklist::getWorkerId).collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(workerIds).stream()
                .collect(Collectors.toMap(User::getId, userRow -> userRow));
        List<EmployerBlacklistDTO> dtos = records.stream().map(black -> {
            EmployerBlacklistDTO dto = new EmployerBlacklistDTO();
            dto.setId(black.getId());
            dto.setWorkerId(black.getWorkerId());
            dto.setReason(black.getReason());
            dto.setCreateTime(black.getCreateTime());
            User worker = userMap.get(black.getWorkerId());
            if (worker != null) {
                dto.setWorkerName(worker.getNickName());
                dto.setWorkerIcon(worker.getIcon());
            }
            return dto;
        }).collect(Collectors.toList());
        return Result.ok(dtos, pageResult.getTotal());
    }
}
