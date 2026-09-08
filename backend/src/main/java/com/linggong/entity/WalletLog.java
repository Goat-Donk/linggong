package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包流水实体，对应表 tb_wallet_log。
 *
 * <p>一次余额变动一条记录：type 标明业务（充值/冻结/解冻/工资/服务费），
 * amount 带符号（正=入账，负=出账），balance_after 为变动后的可用余额，
 * biz_id 关联业务 id（如岗位/结算单），可为空。查询按用户倒序分页。
 */
@Data
@Schema(description = "钱包流水")
@TableName("tb_wallet_log")
public class WalletLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联用户 id（谁的钱变动了） */
    private Long userId;

    /** 流水类型：充值/冻结/解冻/工资/服务费（见 WalletLogType） */
    private String type;

    /** 变动金额（元，正=入账，负=出账，精确到分） */
    private BigDecimal amount;

    /** 变动后余额（元，精确到分） */
    private BigDecimal balanceAfter;

    /** 关联业务 id（如岗位/结算单），可为空 */
    private Long bizId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
