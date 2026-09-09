package com.linggong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linggong.entity.UserInfo;
import org.apache.ibatis.annotations.Insert;

/**
 * 用户资料 Mapper。基础 CRUD 由 MyBatis-Plus 提供。
 */
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    /**
     * 放鸽子次数 +1（已录用后工人单方放弃）。
     *
     * <p>用「INSERT ... ON DUPLICATE KEY UPDATE」做原子 upsert：有资料行则计数 +1；
     * 无资料行（从未产生过任何信用/资料记录的用户）先以默认信用分 100 建行并把计数置 1。
     * 不依赖查后改（防并发重复计数），也不依赖信用扣分（避免信用已到 0 被 clamp 成
     * 「实际变动 0 不记流水」导致计数漏记）。
     *
     * @param userId 放鸽子工人（打工人）id
     */
    @Insert("INSERT INTO tb_user_info (user_id, break_count) VALUES (#{userId}, 1) "
            + "ON DUPLICATE KEY UPDATE break_count = break_count + 1")
    void incrementBreakCount(Long userId);
}
