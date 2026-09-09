package com.linggong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linggong.entity.Job;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

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

    /**
     * 恢复岗位名额（headcount + 1）。撤销报名时退回一个名额，
     * 与 {@link #deductHeadcount(Long)} 对称，保证「报名 → 撤销」后名额归位。
     *
     * @param jobId 岗位 id
     * @return 影响行数
     */
    @Update("UPDATE tb_job SET headcount = headcount + 1 WHERE id = #{jobId}")
    int restoreHeadcount(@Param("jobId") Long jobId);

    /**
     * 汇总某雇主当前仍在岗的担保冻结金额（结算会清零 frozen_amount，
     * 因此该值 = 未结算岗位的冻结款之和，含已下架未结算的岗位）。
     */
    @Select("SELECT COALESCE(SUM(frozen_amount), 0) FROM tb_job WHERE employer_id = #{employerId}")
    BigDecimal sumFrozenByEmployer(@Param("employerId") Long employerId);
}
