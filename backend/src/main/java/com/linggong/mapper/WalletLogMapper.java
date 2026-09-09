package com.linggong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linggong.entity.WalletLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 钱包流水 Mapper。
 */
public interface WalletLogMapper extends BaseMapper<WalletLog> {

    /**
     * 汇总某用户某类型流水的金额（如累计到账工资 / 累计提现）。
     * amount 带符号：工资为正、提现为负，调用方按语义取绝对值。
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM tb_wallet_log "
            + "WHERE user_id = #{userId} AND type = #{type}")
    BigDecimal sumAmount(@Param("userId") Long userId, @Param("type") String type);

    /**
     * 汇总某用户某类型在 start 之后（含）的流水金额，用于「本月工资」等时段统计。
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM tb_wallet_log "
            + "WHERE user_id = #{userId} AND type = #{type} AND create_time >= #{start}")
    BigDecimal sumAmountSince(@Param("userId") Long userId, @Param("type") String type,
                              @Param("start") java.time.LocalDateTime start);
}
