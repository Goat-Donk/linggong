package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 岗位分类实体，对应表 tb_job_category。
 *
 * <p>分类由种子数据预置（发传单、家教辅导、搬运工、促销导购、客服、保洁等），
 * 运行期基本不变，所以后面可以直接做 List 全量缓存。
 */
@Data
@TableName("tb_job_category")
public class JobCategory {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 排序（越小越靠前） */
    private Integer sort;
}
