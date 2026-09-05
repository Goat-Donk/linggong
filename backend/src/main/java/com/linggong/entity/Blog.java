package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 晒单动态实体，对应表 tb_blog。
 *
 * <p>打工人晒今天打的零工。点赞数 liked 冗余存 DB，实际点赞用户用 Redis Set 记录。
 */
@Data
@TableName("tb_blog")
public class Blog {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布用户 id */
    private Long userId;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 图片列表（逗号分隔） */
    private String images;

    /** 点赞数 */
    private Integer liked;

    /** 创建时间 */
    private LocalDateTime createTime;
}
