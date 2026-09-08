package com.linggong.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.Result;
import com.linggong.dto.UserDTO;
import com.linggong.dto.WorkerProfileFormDTO;
import com.linggong.dto.WorkerProfileViewDTO;
import com.linggong.entity.JobCategory;
import com.linggong.entity.User;
import com.linggong.entity.UserInfo;
import com.linggong.entity.WorkerProfile;
import com.linggong.mapper.JobCategoryMapper;
import com.linggong.mapper.UserMapper;
import com.linggong.mapper.WorkerProfileMapper;
import com.linggong.service.IUserInfoService;
import com.linggong.service.IWorkerProfileService;
import com.linggong.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 求职登记服务实现。
 *
 * <p>登记内容与 tb_user 一对一，走 upsert（参照 UserInfo 的 saveOrUpdateByUserId）。
 * 查看他人主页时，把用户基础信息 + 登记内容拼成完整 {@link WorkerProfileViewDTO}，
 * 手机号只给脱敏版（前 3 后 4），防止他人主页泄露真实手机号。
 */
@Service
public class WorkerProfileServiceImpl extends ServiceImpl<WorkerProfileMapper, WorkerProfile>
        implements IWorkerProfileService {

    private final IUserInfoService userInfoService;
    private final UserMapper userMapper;
    private final JobCategoryMapper jobCategoryMapper;

    public WorkerProfileServiceImpl(IUserInfoService userInfoService,
                                    UserMapper userMapper,
                                    JobCategoryMapper jobCategoryMapper) {
        this.userInfoService = userInfoService;
        this.userMapper = userMapper;
        this.jobCategoryMapper = jobCategoryMapper;
    }

    @Override
    public WorkerProfile getByUserId(Long userId) {
        return lambdaQuery().eq(WorkerProfile::getUserId, userId).one();
    }

    @Override
    public Result saveProfile(WorkerProfileFormDTO form) {
        UserDTO current = UserHolder.getUser();
        if (current == null) {
            return Result.fail("请先登录");
        }
        Long userId = current.getId();

        WorkerProfile profile = getByUserId(userId);
        if (profile == null) {
            profile = new WorkerProfile();
            profile.setUserId(userId);
        }
        // 实体：逗号分隔字符串（数组 null/空 → 存空串）
        profile.setTitle(StrUtil.nullToEmpty(form.getTitle()).trim());
        profile.setCategoryIds(joinIds(form.getCategoryIds()));
        profile.setSkillTags(joinStrings(form.getSkillTags()));
        profile.setSalaryMin(form.getSalaryMin());
        profile.setSalaryMax(form.getSalaryMax());
        profile.setWorkTime(joinStrings(form.getWorkTime()));
        profile.setLocation(StrUtil.nullToEmpty(form.getLocation()).trim());
        saveOrUpdate(profile);
        return Result.ok();
    }

    @Override
    public Result viewProfile(Long targetUserId) {
        // 1. 目标用户必须存在
        User user = userMapper.selectById(targetUserId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        UserDTO current = UserHolder.getUser();
        boolean isSelf = current != null && current.getId().equals(targetUserId);

        WorkerProfileViewDTO view = new WorkerProfileViewDTO();
        view.setUserId(user.getId());
        view.setNickName(user.getNickName());
        view.setIcon(user.getIcon());
        view.setRole(user.getRole());
        // 手机号脱敏：138****4338；本人看自己的主页也给脱敏，避免脱敏逻辑分支
        view.setPhone(maskPhone(user.getPhone()));
        view.setIsSelf(isSelf);

        // 2. 基础资料（可选：tb_user_info 可能没建行）
        UserInfo info = userInfoService.getByUserId(targetUserId);
        if (info != null) {
            view.setAge(info.getAge());
            view.setGender(info.getGender());
            view.setIntroduce(info.getIntroduce());
            view.setCredit(info.getCredit());
        }

        // 3. 求职登记内容
        WorkerProfile profile = getByUserId(targetUserId);
        view.setHasProfile(profile != null);
        if (profile != null) {
            view.setTitle(profile.getTitle());
            view.setCategoryIds(splitIds(profile.getCategoryIds()));
            view.setCategoryNames(resolveCategoryNames(profile.getCategoryIds()));
            view.setSkillTags(splitStrings(profile.getSkillTags()));
            view.setSalaryMin(profile.getSalaryMin());
            view.setSalaryMax(profile.getSalaryMax());
            view.setWorkTime(splitStrings(profile.getWorkTime()));
            view.setLocation(profile.getLocation());
            view.setUpdateTime(profile.getUpdateTime());
        }
        return Result.ok(view);
    }

    /** 手机号脱敏：只露前 3 后 4 */
    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone) || phone.length() < 7) {
            return StrUtil.isBlank(phone) ? "" : phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 期望分类 id 逗号串 → 分类名列表（查 tb_job_category） */
    private List<String> resolveCategoryNames(String categoryIds) {
        if (StrUtil.isBlank(categoryIds)) {
            return Collections.emptyList();
        }
        List<Long> ids = splitIds(categoryIds);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, JobCategory> categoryMap = jobCategoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(JobCategory::getId, Function.identity()));
        return ids.stream()
                .map(categoryMap::get)
                .filter(c -> c != null)
                .map(JobCategory::getName)
                .collect(Collectors.toList());
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String joinStrings(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return list.stream().map(s -> StrUtil.nullToEmpty(s).trim())
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(","));
    }

    private List<Long> splitIds(String ids) {
        if (StrUtil.isBlank(ids)) {
            return new ArrayList<>();
        }
        return Arrays.stream(ids.split(","))
                .filter(StrUtil::isNotBlank)
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }

    private List<String> splitStrings(String str) {
        if (StrUtil.isBlank(str)) {
            return new ArrayList<>();
        }
        return Arrays.stream(str.split(","))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}
