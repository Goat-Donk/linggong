package com.linggong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 虚拟钱包实体，对应表 tb_wallet，与 tb_user 一对一（user_id 唯一）。
 *
 * <p>balance 为可用余额（元，精确到分）。担保冻结、工资结算都走这个账户：
 * 发岗时从余额里扣冻结金额，结算时从平台暂扣资金给打工人发工资、给雇主退剩余。
 * 流水统一记录在 tb_wallet_log。
 */
@Data
@Schema(description = "虚拟钱包")
@TableName("tb_wallet")
public class Wallet {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联用户 id（雇主/打工人共用） */
    private Long userId;

    /** 可用余额（元，精确到分） */
    private BigDecimal balance;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
