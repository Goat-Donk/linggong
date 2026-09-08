package com.linggong.utils;

import com.linggong.entity.Job;

import java.time.temporal.ChronoUnit;

/**
 * 岗位任务期计算：任务天数 = 起止时间跨的自然日数。
 *
 * <p>发布担保冻结（日薪×名额×任务天数）与结算计薪封顶（单人最多按任务天数计薪）
 * 必须共用同一口径，故抽成工具类，避免各处自己算出现漂移。
 */
public final class TaskDaysUtil {

    private TaskDaysUtil() {
    }

    /**
     * 任务天数：起止跨自然日数 + 1（例：09-06 08:00 ～ 09-07 18:00 → 2 天）；
     * 未填起止或只填一个时按 1 天（当日一次性任务）。
     */
    public static int taskDays(Job job) {
        if (job.getStartTime() == null || job.getEndTime() == null) {
            return 1;
        }
        long days = ChronoUnit.DAYS.between(job.getStartTime().toLocalDate(), job.getEndTime().toLocalDate()) + 1;
        return days < 1 ? 1 : (int) Math.min(days, Integer.MAX_VALUE);
    }
}
