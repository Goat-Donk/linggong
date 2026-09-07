package com.linggong.utils;

/**
 * 地理距离工具：haversine 球面距离计算。
 *
 * <p>用于岗位列表的「距离排序 / 距离筛选」：当需要按距离升序排序或按半径过滤时，
 * 在内存中对候选岗位计算到用户坐标的直线距离（米）。
 * 与 {@link CoordTransform} 不同，本类只做两点距离，不涉及坐标偏移。
 */
public final class GeoUtil {

    /** 地球平均半径（米） */
    private static final double EARTH_RADIUS = 6371000.0;

    private GeoUtil() {
    }

    /**
     * 计算两点球面距离（haversine 公式）。
     *
     * @param lng1 起点经度
     * @param lat1 起点纬度
     * @param lng2 终点经度
     * @param lat2 终点纬度
     * @return 距离（米）
     */
    public static double distanceMeters(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }
}
