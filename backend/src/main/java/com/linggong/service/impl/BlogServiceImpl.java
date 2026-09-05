package com.linggong.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.BlogDTO;
import com.linggong.dto.BlogFormDTO;
import com.linggong.dto.Result;
import com.linggong.dto.UserDTO;
import com.linggong.entity.Blog;
import com.linggong.mapper.BlogMapper;
import com.linggong.service.IBlogService;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 动态服务实现。
 *
 * <p>点赞用 Redis Set（blog:liked:{blogId}）记录点赞用户，DB liked 字段冗余存点赞数。
 * Feed 推流（发布时推送粉丝收件箱 / 关注时推送历史动态）在 Phase 4 第 3 步实现。
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private final StringRedisTemplate stringRedisTemplate;

    public BlogServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result publish(BlogFormDTO form) {
        Long userId = UserHolder.getUser().getId();
        Blog blog = BeanUtil.copyProperties(form, Blog.class);
        blog.setUserId(userId);
        blog.setLiked(0);
        save(blog);
        // 推送到粉丝收件箱：Phase 4 第 3 步实现
        return Result.ok(blog.getId());
    }

    @Override
    public Result myBlogs(Integer page, Integer pageSize) {
        Long userId = UserHolder.getUser().getId();
        Page<Blog> result = lambdaQuery()
                .eq(Blog::getUserId, userId)
                .orderByDesc(Blog::getCreateTime)
                .page(new Page<>(page, pageSize));

        // 自己的动态，发布者信息直接用登录态（避免查库）
        UserDTO me = UserHolder.getUser();
        List<BlogDTO> dtos = result.getRecords().stream().map(blog -> {
            BlogDTO dto = BeanUtil.copyProperties(blog, BlogDTO.class);
            dto.setIsLike(isLiked(blog.getId(), userId));
            dto.setIcon(me.getIcon());
            dto.setNickName(me.getNickName());
            return dto;
        }).collect(Collectors.toList());

        return Result.ok(dtos, result.getTotal());
    }

    @Override
    public Result like(Long blogId) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + blogId;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        if (Boolean.TRUE.equals(isMember)) {
            // 已点赞 → 取消点赞
            stringRedisTemplate.opsForSet().remove(key, userId.toString());
            baseMapper.decrementLike(blogId);
        } else {
            // 未点赞 → 点赞
            stringRedisTemplate.opsForSet().add(key, userId.toString());
            baseMapper.incrementLike(blogId);
        }
        return Result.ok();
    }

    /**
     * 判断当前用户是否已点赞某条动态。
     */
    private boolean isLiked(Long blogId, Long userId) {
        Boolean member = stringRedisTemplate.opsForSet().isMember(
                RedisConstants.BLOG_LIKED_KEY + blogId, userId.toString());
        return Boolean.TRUE.equals(member);
    }
}
