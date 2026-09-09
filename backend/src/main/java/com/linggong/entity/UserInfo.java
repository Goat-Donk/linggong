package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户资料实体，对应表 tb_user_info，与 tb_user 一对一（user_id 唯一）。
 */
@Data
@Schema(description = "用户资料")
@TableName("tb_user_info")
public class UserInfo {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联用户 id（与 tb_user.id 对应） */
    private Long userId;

    /** 个人简介 */
    private String introduce;

    /** 年龄 */
    private Integer age;

    /** 性别：0 未知，1 男，2 女 */
    private Integer gender;

    /** 信用分（默认 100） */
    private Integer credit;

    /** 放鸽子次数：已录用后工人单方放弃（quit）的累计，全局供雇主审核参考 */
    private Integer breakCount;
}
