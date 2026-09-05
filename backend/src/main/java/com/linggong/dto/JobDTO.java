package com.linggong.dto;

import com.linggong.entity.Job;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 岗位返回对象。
 *
 * <p>继承 {@link Job}，额外带一个 {@code distance} 字段：
 * 附近搜索（GEO）时由 Redis 返回的距离（单位米），普通查询下为 null。
 */
@Data
@Schema(description = "岗位返回对象（含距离）")
@EqualsAndHashCode(callSuper = true)
public class JobDTO extends Job {

    /** 距离（米），附近搜索时填充，普通查询为 null */
    private Double distance;
}
