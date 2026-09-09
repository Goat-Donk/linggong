package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天会话实体，对应表 tb_chat_conversation。
 *
 * <p>一条会话 = 一个岗位 × 一名工人的一对一对话；雇主由岗位发布者决定。
 * 通过 {@code uk_job_worker (job_id, worker_id)} 唯一键保证同一岗位同一工人只有一条会话。
 */
@Data
@TableName("tb_chat_conversation")
public class ChatConversation {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位 id */
    private Long jobId;

    /** 打工人 id */
    private Long workerId;

    /** 雇主 id（冗余自岗位，便于按会话列表查询） */
    private Long employerId;

    /** 最后一条消息预览 */
    private String lastMessage;

    /** 最后消息时间（会话列表排序） */
    private LocalDateTime lastMessageTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
