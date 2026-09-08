package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 求职登记实体，对应表 tb_worker_profile，与 tb_user 一对一（user_id 唯一）。
 *
 * <p>打工人填写一份「我能干什么、期望多少、何时可到岗」的登记，
 * 雇主在审核报名时查看报名人主页可看到这份登记，辅助录用决策。
 */
@Data
@Schema(description = "求职登记")
@TableName("tb_worker_profile")
public class WorkerProfile {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联打工人用户 id（与 tb_user.id 对应） */
    private Long userId;

    /** 求职意向一句话，如「可做传单/导购，周末全天」 */
    private String title;

    /** 期望岗位分类 id（逗号分隔，可多选） */
    private String categoryIds;

    /** 技能标签（逗号分隔） */
    private String skillTags;

    /** 期望日薪下限（元，可空） */
    private Integer salaryMin;

    /** 期望日薪上限（元，可空） */
    private Integer salaryMax;

    /** 可出勤时段（逗号分隔，可多选） */
    private String workTime;

    /** 常驻区域 / 可到岗地点 */
    private String location;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
