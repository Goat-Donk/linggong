package com.linggong.controller;

import com.linggong.dto.Result;
import com.linggong.service.IFollowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关注接口：关注 / 取关 / 是否已关注 / 共同关注。
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    private final IFollowService followService;

    public FollowController(IFollowService followService) {
        this.followService = followService;
    }

    /**
     * 关注 / 取关。
     *
     * @param id       被关注用户 id
     * @param isFollow true 关注 / false 取关
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(id, isFollow);
    }

    /**
     * 是否已关注目标用户。
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id) {
        return followService.isFollow(id);
    }

    /**
     * 与目标用户的共同关注列表。
     */
    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable("id") Long id) {
        return followService.commonFollow(id);
    }
}
