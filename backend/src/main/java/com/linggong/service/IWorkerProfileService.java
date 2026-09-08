package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.Result;
import com.linggong.dto.WorkerProfileFormDTO;
import com.linggong.dto.WorkerProfileViewDTO;
import com.linggong.entity.WorkerProfile;

/**
 * 求职登记服务接口。
 */
public interface IWorkerProfileService extends IService<WorkerProfile> {

    /**
     * 按 userId 查询求职登记（无则返回 null）。
     */
    WorkerProfile getByUserId(Long userId);

    /**
     * 保存当前登录打工人自己的求职登记（不存在则插入，存在则更新）。
     */
    Result saveProfile(WorkerProfileFormDTO form);

    /**
     * 查看某用户的求职登记主页（本人/雇主审核时查看打工人）。
     * 需校验目标用户存在；他人手机号做脱敏。
     */
    Result viewProfile(Long targetUserId);
}
