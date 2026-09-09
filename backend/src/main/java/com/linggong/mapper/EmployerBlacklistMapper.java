package com.linggong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linggong.entity.EmployerBlacklist;

/**
 * 雇主拉黑黑名单 Mapper。基础 CRUD 由 MyBatis-Plus 提供，
 * （employer_id, worker_id）唯一键由建表 DDL 保证。
 */
public interface EmployerBlacklistMapper extends BaseMapper<EmployerBlacklist> {
}
