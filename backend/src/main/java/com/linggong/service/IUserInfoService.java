package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.Result;
import com.linggong.entity.CreditLog;
import com.linggong.entity.UserInfo;

/**
 * 用户资料服务接口。
 */
public interface IUserInfoService extends IService<UserInfo> {

    /**
     * 按 userId（注意：不是主键 id）查询用户资料。
     */
    UserInfo getByUserId(Long userId);

    /**
     * 保存或更新用户资料：按 userId 判断，不存在则插入，存在则更新。
     */
    void saveOrUpdateByUserId(UserInfo userInfo);

    /**
     * 增减用户信用分（无资料行则先按默认 100 起算），结果收敛到 [0,100]。
     *
     * <p>每次调用都会写一条 {@link CreditLog} 信用流水（含原因/变动后分值/业务 id），
     * 供本人在「信用详情」追溯、也作为平台审计依据。信用分不允许用户自行修改。
     *
     * <p>口径（与产品决策一致）：
     * <ul>
     *   <li>互评按 {@link CreditRules#deltaByRating} 调被评价人（原因 EVALUATION）；</li>
     *   <li>单方解除已录用按 {@link CreditRules#BREAK_PENALTY} 扣发起方（原因 BREAK_HIRE）。</li>
     * </ul>
     *
     * @param userId     目标用户
     * @param delta      增减分（正/负/0；0 表示中性事件，不记流水）
     * @param reasonType 变动原因（{@link CreditLog#TYPE_EVALUATION} / {@link CreditLog#TYPE_BREAK_HIRE}）
     * @param bizId      关联业务 id（岗位 id，可为 null）
     * @param remark     给用户看的说明
     */
    void adjustCredit(Long userId, int delta, String reasonType, Long bizId, String remark);

    /**
     * 当前登录用户当前的信用分（无资料行按默认 {@link CreditRules#DEFAULT}）。
     */
    int myCredit();

    /**
     * 当前登录用户的信用流水（分页，按时间倒序）。
     */
    Result creditLogs(Integer page, Integer pageSize);
}
