package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 tb_user。
 *
 * <p>一个用户可兼任两种身份（role 字段）：
 * <ul>
 *   <li>0 —— 打工人（求职者）</li>
 *   <li>1 —— 雇主（发布岗位者）</li>
 * </ul>
 */
@Data
@TableName("tb_user")
public class User {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号（登录账号，唯一） */
    private String phone;

    /** 昵称 */
    private String nickName;

    /** 头像地址 */
    private String icon;

    /** 角色：0 打工人，1 雇主 */
    private Integer role;

    /** 创建时间 */
    private LocalDateTime createTime;
}
