package com.linggong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linggong.entity.JobApplication;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 报名记录 Mapper。基础 CRUD 由 MyBatis-Plus 提供。
 */
public interface JobApplicationMapper extends BaseMapper<JobApplication> {

    /**
     * 结算时把某岗位下所有「已录用(1)」报名批量转「已完成(2)」。
     *
     * @return 影响行数
     */
    @Update("UPDATE tb_job_application SET status = 2 WHERE job_id = #{jobId} AND status = 1")
    int finishByJob(@Param("jobId") Long jobId);
}
