package com.linggong.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linggong.dto.Result;
import com.linggong.dto.UserDTO;
import com.linggong.entity.Follow;
import com.linggong.mapper.FollowMapper;
import com.linggong.mapper.UserMapper;
import com.linggong.service.IFollowService;
import com.linggong.utils.RedisConstants;
import com.linggong.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注服务实现。
 *
 * <p>关注关系双写：
 * <ul>
 *   <li>DB tb_follow：持久化，供 Feed 推流时查粉丝；</li>
 *   <li>Redis Set（follows:{userId}）：快速判断是否关注 + 求共同关注（SINTER）。</li>
 * </ul>
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;

    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate, UserMapper userMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userMapper = userMapper;
    }

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOWS_KEY + userId;

        if (Boolean.TRUE.equals(isFollow)) {
            // 已关注则幂等返回，避免重复插入触发唯一键冲突
            Boolean followed = stringRedisTemplate.opsForSet().isMember(key, followUserId.toString());
            if (Boolean.TRUE.equals(followed)) {
                return Result.ok();
            }
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            save(follow);
            stringRedisTemplate.opsForSet().add(key, followUserId.toString());
        } else {
            // 取关：DB 删记录 + Redis 移除（幂等）
            remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId));
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(
                RedisConstants.FOLLOWS_KEY + userId, followUserId.toString());
        return Result.ok(Boolean.TRUE.equals(isMember));
    }

    @Override
    public Result commonFollow(Long targetUserId) {
        Long userId = UserHolder.getUser().getId();
        // 求两个用户关注集合的交集
        Set<String> common = stringRedisTemplate.opsForSet().intersect(
                RedisConstants.FOLLOWS_KEY + userId,
                RedisConstants.FOLLOWS_KEY + targetUserId);
        if (common == null || common.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = common.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userMapper.selectBatchIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
