package com.linggong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linggong.entity.Job;
import com.linggong.entity.JobSettlement;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/** 岗位结算单 Mapper。 */
public interface JobSettlementMapper extends BaseMapper<JobSettlement> {

    @Select("SELECT * FROM tb_job_settlement WHERE job_id = #{jobId} LIMIT 1")
    JobSettlement selectByJobId(@Param("jobId") Long jobId);

    /** 到期且尚未结算、仍有冻结款的岗位，供定时任务逐岗处理。 */
    @Select("SELECT j.* FROM tb_job j LEFT JOIN tb_job_settlement s ON s.job_id = j.id "
            + "WHERE j.end_time IS NOT NULL AND j.end_time <= NOW() "
            + "AND j.frozen_amount > 0 AND s.id IS NULL ORDER BY j.end_time ASC LIMIT #{limit}")
    List<Job> selectExpiredUnsettledJobs(@Param("limit") int limit);

    /** 汇总某雇主全部结算单的平台服务费（累计抽成，雇主口径）。 */
    @Select("SELECT COALESCE(SUM(service_fee), 0) FROM tb_job_settlement WHERE employer_id = #{employerId}")
    BigDecimal sumServiceFeeByEmployer(@Param("employerId") Long employerId);
}
