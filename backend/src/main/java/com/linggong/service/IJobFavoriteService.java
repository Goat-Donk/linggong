package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.Result;
import com.linggong.entity.JobFavorite;

/**
 * 岗位收藏服务接口。
 */
public interface IJobFavoriteService extends IService<JobFavorite> {

    /**
     * 收藏 / 取消收藏岗位（幂等，DB + Redis 双写）。
     */
    Result favorite(Long jobId, Boolean isFavorite);

    /**
     * 是否已收藏该岗位。
     */
    Result isFavorite(Long jobId);

    /**
     * 我的收藏分页查询（含岗位信息）。
     */
    Result myFavorites(Integer page, Integer pageSize);
}
