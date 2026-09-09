package com.linggong.service;

import com.linggong.dto.BlacklistFormDTO;
import com.linggong.dto.Result;

/**
 * 雇主拉黑打工人服务。
 *
 * <p>设计约定（与产品决策一致）：
 * <ul>
 *   <li><b>全局生效</b>：黑名单以（employer_id, worker_id）唯一，拉黑后该工人无法报名
 *       该雇主任何岗位；</li>
 *   <li><b>防滥用上限</b>：单雇主最多同时拉黑 {@link #MAX_ACTIVE_BLACKLIST} 人，满则须先解除；</li>
 *   <li><b>可解除</b>：雇主可随时把工人移出黑名单（解除后可再次报名）；</li>
 *   <li><b>静默</b>：拉黑 / 解除均不通知被打工人（只有工人报名时在接口返回被拦原因）。</li>
 * </ul>
 */
public interface IEmployerBlacklistService {

    /** 单雇主最多同时拉黑的打工人数（防滥用：避免把潜在报名者全部拉黑的操作性） */
    int MAX_ACTIVE_BLACKLIST = 50;

    /**
     * 雇主拉黑一名打工人。要求：目标存在且是打工人（role=0）、未在黑名单、未达上限；
     * 侧效：自动取消该工人对该雇主所有岗位的「待确认」报名并释放名额，避免黑名单与审核矛盾。
     */
    Result add(BlacklistFormDTO form);

    /**
     * 雇主把一名打工人移出黑名单（解除后可再次报名）。
     */
    Result remove(Long workerId);

    /**
     * 我的黑名单（分页，按拉黑时间倒序），含被打工人昵称/头像，供管理列表展示与解除。
     */
    Result myList(Integer page, Integer pageSize);
}
