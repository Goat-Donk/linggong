package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 关注关系实体，对应表 tb_follow。
 *
 * <p>关注关系同时落在 DB（tb_follow，供 Feed 推流查粉丝）和 Redis（Set，供快速判断/共同关注）。
 */
@Data
@TableName("tb_follow")
public class Follow {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关注者 id */
    private Long userId;

    /** 被关注者 id */
    private Long followUserId;
}
