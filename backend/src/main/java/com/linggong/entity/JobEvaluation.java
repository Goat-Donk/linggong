package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 互评实体，对应表 tb_job_evaluation。
 *
 * <p>岗位完成后的互评：雇主评工人 / 工人评雇主，评分 1-5 分。
 */
@Data
@TableName("tb_job_evaluation")
public class JobEvaluation {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位 id */
    private Long jobId;

    /** 评价人 id */
    private Long fromUserId;

    /** 被评价人 id */
    private Long toUserId;

    /** 评分 1-5 */
    private Integer rating;

    /** 评价内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;
}
