package com.linggong.utils;

import com.linggong.entity.Attendance;

/**
 * 考勤计薪口径：把一条考勤终态折算成「半天数」。
 *
 * <p>核心规则：到岗+下工都通过 = 2 半天（1 天）；仅到岗通过 = 1 半天（0.5 天）；否则 0。
 * 用整数半天而非浮点天数参与账务，避免浮点误差；结算与打工人端累计展示共用此口径。
 */
public final class AttendancePayUtil {

    private AttendancePayUtil() {
    }

    /**
     * 单条考勤记录折算的计薪半天数：0 / 1 / 2。
     */
    public static int halfDays(Attendance row) {
        if (row == null || row.getOnStatus() == null || row.getOnStatus() != 2) {
            return 0;
        }
        return row.getOffStatus() != null && row.getOffStatus() == 2 ? 2 : 1;
    }
}
