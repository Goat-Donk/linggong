package com.linggong.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Agent 工具输出专用的日期格式化。
 *
 * <p>工具 JSON 里的时间若以 ISO 字符串（如 2026-09-08T16:09:00）返回给 LLM，
 * deepseek-v3 转述时容易篡改（实测把 2026-09-08 说成 2023-07-10）。
 * 这里统一预格式化为可直接引用的中文串，让模型原样转述、没有换算余地。
 * 空值一律返回 null，模型看到 null 就不该提日期。
 */
public final class AiToolDate {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");

    private AiToolDate() {
    }

    /** 只到日：2026年9月8日 */
    public static String day(LocalDate date) {
        return date == null ? null : date.format(DATE);
    }

    /** 到分钟：2026年9月8日 16:09 */
    public static String datetime(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME);
    }
}
