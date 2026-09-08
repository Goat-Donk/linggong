package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位收藏实体，对应表 tb_job_favorite。
 *
 * <p>收藏关系同时落在 DB（tb_job_favorite，供收藏列表分页查询）和
 * Redis（Set，供详情页快速判断是否已收藏），与关注（tb_follow）模式一致。
 */
@Data
@Schema(description = "岗位收藏")
@TableName("tb_job_favorite")
public class JobFavorite {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收藏者 id */
    private Long userId;

    /** 岗位 id */
    private Long jobId;

    /** 收藏时间 */
    private LocalDateTime createTime;
}
