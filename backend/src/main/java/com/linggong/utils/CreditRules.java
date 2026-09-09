package com.linggong.utils;

/**
 * 信用分规则：互评与履约行为如何增减信用分（信用分存 tb_user_info.credit，范围 0~100）。
 *
 * <p>口径（与产品决策一致）：
 * <ul>
 *   <li>互评打分折算：5 星 +2、4 星 +1、3 星不变、1~2 星 −3（被评价人受影响）；</li>
 *   <li>放鸽子：单方解除已确认录用（工人放弃 / 雇主取消录用）的发起方 −10。</li>
 * </ul>
 */
public final class CreditRules {

    public static final int MAX = 100;
    public static final int MIN = 0;

    /** 用户初始信用分（与 tb_user_info.credit 的 DB 默认值一致） */
    public static final int DEFAULT = 100;

    /** 放鸽子/单方解除已录用岗位的信用扣分 */
    public static final int BREAK_PENALTY = -10;

    /**
     * 低信用雇主判定阈值：雇主信用分低于该值即视为「低信用」，
     * 其发布的岗位在默认曝光列表（首页「最新」）会被降权，排到正常雇主岗位之后。
     *
     * <p>口径说明：雇主初始 100，放鸽子一次 −10、收差评 −3。正常履约的雇主很难跌破 60，
     * 跌破 60 通常意味着多次违约/差评，已属风险雇主，适合触发曝光降权；
     * 而一次小失误（−10 到 90、−3 到 97）不影响曝光，避免降权伤及正常经营。</p>
     */
    public static final int LOW_CREDIT = 60;

    private CreditRules() {
    }

    /**
     * 评分 → 信用分增减。
     *
     * @param rating 1~5
     * @return 增减分（可正可负可 0）
     */
    public static int deltaByRating(int rating) {
        if (rating >= 5) {
            return 2;
        }
        if (rating == 4) {
            return 1;
        }
        if (rating <= 2) {
            return -3;
        }
        return 0; // 3 星：中性
    }

    /**
     * 把信用分收敛到 [MIN, MAX]。
     */
    public static int clamp(int credit) {
        return Math.max(MIN, Math.min(MAX, credit));
    }

    /**
     * 是否低信用（触发岗位曝光降权 / 撮合风险提示）。
     *
     * @param credit 信用分；null 视为无记录，按默认满分 100 处理（不降权）
     */
    public static boolean isLowCredit(Integer credit) {
        return (credit == null ? DEFAULT : credit) < LOW_CREDIT;
    }
}
