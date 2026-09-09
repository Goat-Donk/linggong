package com.linggong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linggong.entity.Job;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

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

    /**
     * 默认「最新」曝光列表分页（不经过 MyBatis-Plus 分页插件，见下方说明）：
     * 上架岗位 LEFT JOIN 雇主信用分，低信用雇主（credit &lt; lowCredit）的岗位整体降权沉底
     * （排到正常雇主岗位之后），组内仍按最新优先、同一秒按 id 倒序，保证分页稳定。
     *
     * <p>正常雇主岗位相对位置与原来一致（create_time DESC, id DESC），只把低信用雇主的岗位
     * 从时间流里挪到队尾，即「曝光降权」。总条数由调用方用同等筛选条件的 count 另查。</p>
     *
     * <p>为何不用 MyBatis-Plus 的 Page：分页插件会改写 ORDER BY 里跨表别名（ui.credit）的语句，
     * 生成外层 COUNT 时丢失 JOIN 别名作用域报 Unknown column。教学数据量小，这里用
     * SQL 自带 LIMIT 直接翻页，语义等价且稳定。</p>
     */
    @Select("<script>"
            + "SELECT j.* FROM tb_job j "
            + "LEFT JOIN tb_user_info ui ON ui.user_id = j.employer_id "
            + "WHERE j.status = 0 "
            + "<if test='categoryId != null'>AND j.category_id = #{categoryId} </if>"
            + "<if test='keyword != null and keyword.trim() != \"\"'>AND j.name LIKE CONCAT('%', #{keyword}, '%') </if>"
            + "<if test='minSalary != null'>AND j.salary &gt;= #{minSalary} </if>"
            + "<if test='maxSalary != null'>AND j.salary &lt;= #{maxSalary} </if>"
            + "ORDER BY IF(COALESCE(ui.credit, #{fullCredit}) &lt; #{lowCredit}, 1, 0), "
            + "j.create_time DESC, j.id DESC "
            + "LIMIT #{offset}, #{size}"
            + "</script>")
    List<Job> selectExposurePage(@Param("categoryId") Long categoryId,
                                 @Param("keyword") String keyword,
                                 @Param("minSalary") Integer minSalary,
                                 @Param("maxSalary") Integer maxSalary,
                                 @Param("lowCredit") Integer lowCredit,
                                 @Param("fullCredit") Integer fullCredit,
                                 @Param("offset") int offset,
                                 @Param("size") int size);
}
