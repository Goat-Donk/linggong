package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日考勤记录，对应表 tb_attendance。
 *
 * <p>字段约定：
 * <ul>
 *   <li>一人一岗一天一条（uk_job_worker_date 唯一键），缺省则无记录（视为未到）</li>
 *   <li>on_status / off_status —— 到岗 / 下工各自核销状态：0 未申请 / 1 待核销 / 2 通过 / 3 驳回</li>
 *   <li>计薪依据（结算时按终态推导）：到岗+下工都通过=1 天；仅到岗通过=0.5 天；否则 0</li>
 * </ul>
 */
@Data
@TableName("tb_attendance")
public class Attendance {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位 id */
    private Long jobId;

    /** 打工人 id */
    private Long workerId;

    /** 出勤日期 */
    private LocalDate workDate;

    /** 到岗核销状态：0 未申请 / 1 待核销 / 2 通过 / 3 驳回 */
    private Integer onStatus;

    /** 下工核销状态：0 未申请 / 1 待核销 / 2 通过 / 3 驳回 */
    private Integer offStatus;

    /** 到岗申请时间 */
    private LocalDateTime onTime;

    /** 下工申请时间 */
    private LocalDateTime offTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
