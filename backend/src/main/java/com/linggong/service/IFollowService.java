package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.Result;
import com.linggong.entity.Follow;

/**
 * 关注服务接口：关注 / 取关 / 是否已关注 / 共同关注。
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注 / 取关某个用户。
     *
     * @param followUserId 被关注者 id
     * @param isFollow     true 关注，false 取关
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 判断当前用户是否已关注目标用户。
     */
    Result isFollow(Long followUserId);

    /**
     * 查询当前用户与目标用户的共同关注列表。
     */
    Result commonFollow(Long targetUserId);
}
