package com.linggong.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.BlogDTO;
import com.linggong.dto.BlogFormDTO;
import com.linggong.dto.Result;
import com.linggong.dto.ScrollResult;
import com.linggong.dto.UserDTO;
import com.linggong.entity.Blog;
import com.linggong.entity.Follow;
import com.linggong.entity.User;
import com.linggong.mapper.BlogMapper;
import com.linggong.mapper.FollowMapper;
import com.linggong.mapper.UserMapper;
import com.linggong.service.IBlogService;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 动态服务实现。
 *
 * <p>点赞用 Redis Set（blog:liked:{blogId}）记录点赞用户，DB liked 字段冗余存点赞数。
 * Feed 推流：
 * <ul>
 *   <li>发布动态 → 推送给作者所有粉丝收件箱（feed:{粉丝id} ZSet）；</li>
 *   <li>关注 → 把对方最近的历史动态滚入当前用户收件箱；</li>
 *   <li>关注的人动态 → 滚动分页（lastId + offset）读收件箱。</li>
 * </ul>
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    /** Feed 滚动分页默认每页条数 */
    private static final int FEED_PAGE_SIZE = 3;

    private final StringRedisTemplate stringRedisTemplate;
    private final FollowMapper followMapper;
    private final UserMapper userMapper;

    public BlogServiceImpl(StringRedisTemplate stringRedisTemplate,
                           FollowMapper followMapper,
                           UserMapper userMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.followMapper = followMapper;
        this.userMapper = userMapper;
    }

    @Override
    public Result publish(BlogFormDTO form) {
        Long userId = UserHolder.getUser().getId();
        Blog blog = BeanUtil.copyProperties(form, Blog.class);
        blog.setUserId(userId);
        blog.setLiked(0);
        save(blog);

        // 推流：把新动态推送给所有粉丝的收件箱，并推进作者自己的收件箱
        // （自己的动态也应出现在「动态」页顶部，符合常见时间线设计）
        pushBlogToFollowers(userId, blog.getId());
        stringRedisTemplate.opsForZSet().add(
                RedisConstants.FEED_KEY + userId, blog.getId().toString(), System.currentTimeMillis());

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
    public Result queryBlogOfFollow(Long lastId, Integer offset, Integer pageSize) {
        Long userId = UserHolder.getUser().getId();
        // 首次查询 lastId 为空，用当前时间作为上界（feed 的 score 都是过去的时间戳）
        long max = (lastId == null || lastId <= 0) ? System.currentTimeMillis() : lastId;
        int os = offset == null ? 0 : offset;
        int size = pageSize == null || pageSize <= 0 ? FEED_PAGE_SIZE : pageSize;

        // ZREVRANGEBYSCORE feed:{userId} max 0 LIMIT offset size（score 从高到低）
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(RedisConstants.FEED_KEY + userId, 0, max, os, size);
        if (tuples == null || tuples.isEmpty()) {
            return Result.ok();
        }

        // 解析动态 id、本次最小时间戳 minTime、以及 minTime 对应的元素个数（下一页 offset）
        List<Long> ids = new ArrayList<>(tuples.size());
        long minTime = 0;
        int nextOffset = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                nextOffset++;
            } else {
                minTime = time;
                nextOffset = 1;
            }
        }

        // 按 ZSet 顺序回查动态（用 Map 重排，避免 ORDER BY FIELD 裸 SQL）
        List<Blog> blogs = lambdaQuery().in(Blog::getId, ids).list();
        Map<Long, Blog> blogMap = blogs.stream()
                .collect(Collectors.toMap(Blog::getId, b -> b, (a, b) -> a));
        List<Blog> ordered = ids.stream().map(blogMap::get)
                .filter(Objects::nonNull).collect(Collectors.toList());

        // 批量查发布者信息，避免 N+1
        Set<Long> authorIds = ordered.stream().map(Blog::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = authorIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<BlogDTO> dtos = ordered.stream().map(blog -> {
            BlogDTO dto = BeanUtil.copyProperties(blog, BlogDTO.class);
            dto.setIsLike(isLiked(blog.getId(), userId));
            dto.setIsFollow(isFollowed(blog.getUserId(), userId));
            User author = userMap.get(blog.getUserId());
            if (author != null) {
                dto.setIcon(author.getIcon());
                dto.setNickName(author.getNickName());
            }
            return dto;
        }).collect(Collectors.toList());

        return Result.ok(new ScrollResult(dtos, minTime, nextOffset));
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
     * 把某条动态推送给作者的所有粉丝收件箱（ZSet，member=动态 id，score=时间戳）。
     */
    private void pushBlogToFollowers(Long authorId, Long blogId) {
        List<Follow> follows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowUserId, authorId));
        if (follows == null || follows.isEmpty()) {
            return;
        }
        long score = System.currentTimeMillis();
        for (Follow follow : follows) {
            stringRedisTemplate.opsForZSet().add(
                    RedisConstants.FEED_KEY + follow.getUserId(), blogId.toString(), score);
        }
    }

    /**
     * 判断当前用户是否已点赞某条动态。
     */
    private boolean isLiked(Long blogId, Long userId) {
        Boolean member = stringRedisTemplate.opsForSet().isMember(
                RedisConstants.BLOG_LIKED_KEY + blogId, userId.toString());
        return Boolean.TRUE.equals(member);
    }

    /**
     * 判断当前用户是否已关注某用户（读 Redis 关注集合，避免查库）。
     */
    private boolean isFollowed(Long followUserId, Long userId) {
        Boolean member = stringRedisTemplate.opsForSet().isMember(
                RedisConstants.FOLLOWS_KEY + userId, followUserId.toString());
        return Boolean.TRUE.equals(member);
    }
}
