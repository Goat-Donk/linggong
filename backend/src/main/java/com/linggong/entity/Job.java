package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 零工岗位实体，对应表 tb_job。
 *
 * <p>字段约定：
 * <ul>
 *   <li>x —— 经度，y —— 纬度（对齐黑马点评命名，用于 GEO 附近搜索）</li>
 *   <li>status —— 0 上架 / 1 下架</li>
 * </ul>
 */
@Data
@Schema(description = "零工岗位")
@TableName("tb_job")
public class Job {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类 id（关联 tb_job_category） */
    private Long categoryId;

    /** 雇主（发布者）id，关联 tb_user */
    private Long employerId;

    /** 岗位名称 */
    private String name;

    /** 工作地址 */
    private String address;

    /** 经度 */
    private Double x;

    /** 纬度 */
    private Double y;

    /** 薪资（日薪，元/天），可空 */
    private Integer salary;

    /** 名额 */
    private Integer headcount;

    /** 已担保冻结金额（元，精确到分）：发布时冻结 = 日薪×名额×任务天数 */
    private BigDecimal frozenAmount;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 岗位描述 */
    private String description;

    /** 状态：0 上架 / 1 下架 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
