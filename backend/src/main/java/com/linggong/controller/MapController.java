package com.linggong.controller;

import com.linggong.dto.Result;
import com.linggong.service.IMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 地图接口：逆地理编码（经纬度 → 文字地址）。
 *
 * <p>走后端代理调高德，需登录（未加入放行白名单，默认拦截），仅发布岗位流程用到。
 */
@Tag(name = "地图接口", description = "逆地理编码（坐标转地址，高德）")
@RestController
@RequestMapping("/map")
public class MapController {

    private final IMapService mapService;

    public MapController(IMapService mapService) {
        this.mapService = mapService;
    }

    @Operation(summary = "逆地理编码：经纬度转文字地址")
    @GetMapping("/regeo")
    public Result regeo(
            @Parameter(description = "经度（WGS84）") @RequestParam("x") Double x,
            @Parameter(description = "纬度（WGS84）") @RequestParam("y") Double y) {
        return mapService.regeo(x, y);
    }
}
