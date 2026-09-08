package com.linggong.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 求职登记展示对象（雇主查看打工人简历页）。
 *
 * <p>除了登记内容，还携带该用户的基础信息（昵称/头像/年龄/性别/简介/信用分），
 * 以及脱敏后的手机号（只露出前 3 后 4），方便一次请求组装完整主页。
 * hasProfile = false 表示该用户还没填过登记，前端展示空态。
 */
@Data
@Schema(description = "求职登记展示对象")
public class WorkerProfileViewDTO {

    // ---------- 基础信息（来自 tb_user / tb_user_info） ----------

    /** 用户 id */
    private Long userId;

    /** 昵称 */
    private String nickName;

    /** 头像 */
    private String icon;

    /** 角色：0 打工人 1 雇主 */
    private Integer role;

    /** 年龄 */
    private Integer age;

    /** 性别：0 未知 1 男 2 女 */
    private Integer gender;

    /** 个人简介 */
    private String introduce;

    /** 信用分 */
    private Integer credit;

    /** 脱敏手机号（前 3 后 4，如 138****4338） */
    private String phone;

    // ---------- 求职登记内容 ----------

    /** 是否已填写求职登记 */
    private Boolean hasProfile;

    /** 求职意向一句话 */
    private String title;

    /** 期望岗位分类 id */
    private List<Long> categoryIds;

    /** 期望岗位分类名 */
    private List<String> categoryNames;

    /** 技能标签 */
    private List<String> skillTags;

    /** 期望日薪下限（元） */
    private Integer salaryMin;

    /** 期望日薪上限（元） */
    private Integer salaryMax;

    /** 可出勤时段 */
    private List<String> workTime;

    /** 常驻区域 / 可到岗地点 */
    private String location;

    /** 是否查看的是自己 */
    private Boolean isSelf;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
