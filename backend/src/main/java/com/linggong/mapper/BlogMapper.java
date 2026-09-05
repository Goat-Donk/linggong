package com.linggong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linggong.entity.Blog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 动态 Mapper。基础 CRUD 由 MyBatis-Plus 提供。
 */
public interface BlogMapper extends BaseMapper<Blog> {

    /**
     * 点赞数 +1。
     */
    @Update("UPDATE tb_blog SET liked = liked + 1 WHERE id = #{id}")
    int incrementLike(@Param("id") Long id);

    /**
     * 点赞数 -1（liked > 0 才扣，防止负数）。
     */
    @Update("UPDATE tb_blog SET liked = liked - 1 WHERE id = #{id} AND liked > 0")
    int decrementLike(@Param("id") Long id);
}
