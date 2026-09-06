package com.linggong.utils;

/**
 * 坐标转换工具：WGS84（GPS）→ GCJ-02（火星坐标）。
 *
 * <p>浏览器 {@code navigator.geolocation} 返回的是 WGS84（GPS）坐标，
 * 而高德地图的逆地理编码接口要求 GCJ-02（火星坐标），两者在国内偏差约 100~700 米，
 * 直接传会导致地址解析偏到别的街道。故调用高德前先做一次转换。
 *
 * <p>本类只实现「WGS84 → GCJ-02」单向转换（逆地理编码只需要这一个方向），
 * 算法为公开的通用实现；境外坐标不在火星偏移范围内，原样返回。
 */
public final class CoordTransform {

    /** 长半轴（克拉索夫斯基椭球） */
    private static final double A = 6378245.0;
    /** 第一偏心率的平方 */
    private static final double EE = 0.00669342162296594323;

    private CoordTransform() {
    }

    /**
     * WGS84 → GCJ-02。
     *
     * @param lon 经度（WGS84）
     * @param lat 纬度（WGS84）
     * @return [经度, 纬度]（GCJ-02）
     */
    public static double[] wgs84ToGcj02(double lon, double lat) {
        if (outOfChina(lon, lat)) {
            return new double[]{lon, lat};
        }
        double dLat = transformLat(lon - 105.0, lat - 35.0);
        double dLon = transformLon(lon - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI);
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new double[]{lon + dLon, lat + dLat};
    }

    /** 是否在中国境外（境外不做火星偏移） */
    private static boolean outOfChina(double lon, double lat) {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y
                + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y
                + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }
}
