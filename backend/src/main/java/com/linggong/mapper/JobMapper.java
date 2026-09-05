package com.linggong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linggong.entity.Job;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 岗位 Mapper。基础 CRUD 由 MyBatis-Plus 提供。
 */
public interface JobMapper extends BaseMapper<Job> {

    /**
     * 扣减岗位名额（headcount - 1）。WHERE headcount &gt; 0 保证不会扣成负数。
     *
     * @param jobId 岗位 id
     * @return 影响行数（0 表示名额已为 0 或岗位不存在）
     */
    @Update("UPDATE tb_job SET headcount = headcount - 1 WHERE id = #{jobId} AND headcount > 0")
    int deductHeadcount(@Param("jobId") Long jobId);
}
