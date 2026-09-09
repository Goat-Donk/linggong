package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知实体，对应表 tb_notification。
 *
 * <p>通知是「被动送达」：审核结果、结算工资等关键动作发生后，由业务方调用
 * {@code notify} 写入一条记录，接收人在前端通知中心看到。只做站内落地，不做推送。
 */
@Data
@TableName("tb_notification")
public class Notification {

    /** 通知类型：报名被录用 */
    public static final String TYPE_APPLY_APPROVED = "APPLY_APPROVED";
    /** 通知类型：报名被拒绝 */
    public static final String TYPE_APPLY_REJECTED = "APPLY_REJECTED";
    /** 通知类型：结算工资到账 */
    public static final String TYPE_SETTLE_WAGE = "SETTLE_WAGE";
    /** 通知类型：工人放弃已录用岗位 */
    public static final String TYPE_APPLY_QUIT = "APPLY_QUIT";
    /** 通知类型：雇主取消已录用 */
    public static final String TYPE_APPLY_DISMISS = "APPLY_DISMISS";

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 id */
    private Long userId;

    /** 通知类型：APPLY_APPROVED / APPLY_REJECTED / SETTLE_WAGE */
    private String type;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 关联业务 id（岗位 id），前端用于跳转到岗位详情 */
    private Long bizId;

    /** 是否已读：0 未读 / 1 已读 */
    private Integer readFlag;

    /** 创建时间 */
    private LocalDateTime createTime;
}
