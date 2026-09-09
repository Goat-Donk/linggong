package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报名记录实体（订单），对应表 tb_job_application。
 *
 * <p>字段约定：
 * <ul>
 *   <li>id —— 由 RedisIdWorker 雪花算法生成，不用数据库自增</li>
 *   <li>status —— 0 待确认 / 1 已录用 / 2 已完成 / 3 已取消</li>
 * </ul>
 *
 * <p>防重复报名靠「Redis 一人一单标记（apply:order:{jobId}，Lua 原子）」主防线；
 * 撤销/被拒后删除该标记即可再次报名，同一工人对同一岗位可有多条历史记录。
 */
@Data
@TableName("tb_job_application")
public class JobApplication {

    /** 主键，雪花算法生成，手动赋值 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 岗位 id */
    private Long jobId;

    /** 打工人 id */
    private Long workerId;

    /** 状态：0 待确认 / 1 已录用 / 2 已完成 / 3 已取消 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
