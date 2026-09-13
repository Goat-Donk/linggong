package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台规则知识库条目（AI 问答的规则依据）。
 *
 * <p>内容全部对齐现网业务实现（结算/信用/考勤/黑名单等口径与 db.sql 和业务代码一致），
 * 启动时由 {@link com.linggong.ai.rule.PlatformRuleBook} 全量载入并注入系统提示词。
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
