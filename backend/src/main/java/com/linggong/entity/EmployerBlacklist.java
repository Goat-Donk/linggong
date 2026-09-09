package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 雇主拉黑打工人黑名单，对应表 tb_employer_blacklist。
 *
 * <p>全局生效：黑名单以（employer_id, worker_id）唯一，拉黑后该工人无法再报名
 * 该雇主发布的任何岗位（含未来新岗位）。静默设计：拉黑不通知被打工人。</p>
 */
@Data
@Schema(description = "雇主拉黑打工人黑名单")
@TableName("tb_employer_blacklist")
public class EmployerBlacklist {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 拉黑发起方（雇主）id */
    private Long employerId;

    /** 被打工人 id */
    private Long workerId;

    /** 拉黑原因（雇主侧可见，不展示给被打工人） */
    private String reason;

    /** 创建时间 */
    private LocalDateTime createTime;
}
