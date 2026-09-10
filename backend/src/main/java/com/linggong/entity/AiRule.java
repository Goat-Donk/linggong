package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台规则知识库条目（AI 问答 RAG 的数据源）。
 *
 * <p>内容全部对齐现网业务实现（结算/信用/考勤/黑名单等口径与 db.sql 和业务代码一致），
 * 启动时由 Bm25ContentRetriever 全量载入内存做关键词检索。
 */
@Data
@TableName("tb_ai_rule")
public class AiRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则标题（检索展示用） */
    private String title;

    /** 检索标签（逗号分隔，命中词，如：结算,工资,服务费） */
    private String tags;

    /** 规则正文：可直接引用的口语化完整回答 */
    private String content;

    private LocalDateTime createTime;
}
