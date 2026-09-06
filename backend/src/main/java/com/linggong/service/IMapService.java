package com.linggong.service;

import com.linggong.dto.Result;

/**
 * 地图服务：逆地理编码（经纬度 → 文字地址）。
 */
public interface IMapService {

    /**
     * 逆地理编码：把经纬度转成高德返回的「格式化地址」。
     *
     * @param x 经度（WGS84）
     * @param y 纬度（WGS84）
     * @return 成功时 data 为文字地址；失败返回 errorMsg
     */
    Result regeo(Double x, Double y);
}
