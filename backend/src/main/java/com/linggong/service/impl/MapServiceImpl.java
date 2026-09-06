package com.linggong.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.linggong.dto.Result;
import com.linggong.service.IMapService;
import com.linggong.utils.CoordTransform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 地图服务实现：走后端代理调高德「逆地理编码」接口。
 *
 * <p>为什么不前端直调高德：高德 Web服务 Key 若暴露在浏览器，任何人都能取走刷额度；
 * 走后端代理，Key 只存在服务端（application.yml）。HTTP 调用用 Spring 6.1 的 {@link RestClient}（同步）。
 */
@Slf4j
@Service
public class MapServiceImpl implements IMapService {

    private final String amapKey;
    private final RestClient restClient;

    public MapServiceImpl(@Value("${linggong.amap.key:}") String amapKey) {
        this.amapKey = amapKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Result regeo(Double x, Double y) {
        if (amapKey == null || amapKey.isBlank()) {
            return Result.fail("未配置高德地图 Key");
        }
        // 浏览器 geolocation 是 WGS84，高德 regeo 要 GCJ-02，先转坐标
        double[] gcj = CoordTransform.wgs84ToGcj02(x, y);
        String url = String.format(
                "https://restapi.amap.com/v3/geocode/regeo?key=%s&location=%.6f,%.6f&extensions=base",
                amapKey, gcj[0], gcj[1]);
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            return parseAddress(body);
        } catch (Exception e) {
            log.error("逆地理编码调用失败：x={}, y={}", x, y, e);
            return Result.fail("地址解析失败，请稍后重试或手动填写地址");
        }
    }

    /**
     * 解析高德 regeo 返回，取出「格式化地址」（regeocode.formatted_address）。
     */
    private Result parseAddress(String body) {
        JSONObject json = JSONUtil.parseObj(body);
        if (!"1".equals(json.getStr("status"))) {
            String info = json.getStr("info");
            return Result.fail("地址解析失败：" + (info == null ? "未知错误" : info));
        }
        JSONObject regeocode = json.getJSONObject("regeocode");
        if (regeocode == null) {
            return Result.fail("地址解析失败：无结果");
        }
        return Result.ok(regeocode.getStr("formatted_address"));
    }
}
