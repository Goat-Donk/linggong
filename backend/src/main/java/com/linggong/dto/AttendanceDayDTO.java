package com.linggong.dto;

import lombok.Data;

/**
 * 某人某天考勤视图（打工人「我的考勤」逐日记录与雇主「按日核销」列表共用）。
 *
 * <p>dayText 为状态文案；dayValue 为当前状态推导的计薪天数
 * （结算按最终核销终态重新推导，此处仅供展示）。
 */
@Data
public class AttendanceDayDTO {

    /** 打工人 id（雇主核销列表必填，打工人自己的记录可空） */
    private Long workerId;

    /** 打工人昵称 */
    private String workerName;

    /** 打工人头像 */
    private String workerIcon;

    /** 出勤日期 yyyy-MM-dd */
    private String workDate;

    /** 到岗核销状态：0 未申请 / 1 待核销 / 2 通过 / 3 驳回 */
    private Integer onStatus;

    /** 下工核销状态：0 未申请 / 1 待核销 / 2 通过 / 3 驳回 */
    private Integer offStatus;

    /** 到岗申请时间 */
    private String onTime;

    /** 下工申请时间 */
    private String offTime;

    /** 当天是否为今天（前端高亮当天） */
    private Boolean today;

    /** 状态文案 */
    private String dayText;

    /** 计薪天数（0 / 0.5 / 1），仅供展示 */
    private Double dayValue;
}
