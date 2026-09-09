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
}
