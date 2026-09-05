package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 滚动分页结果（Feed 用）。
 *
 * <p>与普通分页（page + pageSize）不同，滚动分页按时间戳定位游标：
 * <ul>
 *   <li>{@code minTime}：本次返回的最小时间戳，作为下一页的 lastId；</li>
 *   <li>{@code offset}：本次返回中与 minTime 相同的元素个数，下一页据此跳过已返回的元素。</li>
 * </ul>
 * 之所以要 offset，是因为同一个时间戳可能对应多条动态，仅靠 minTime 定位会漏数据。
 */
@Data
@Schema(description = "滚动分页结果（Feed 关注流）")
public class ScrollResult {

    /** 本次返回的动态列表 */
    private List<BlogDTO> list;

    /** 本次返回的最小时间戳（下一页的 lastId） */
    private Long minTime;

    /** 偏移量：本次返回中与 minTime 相同的元素个数 */
    private Integer offset;

    public ScrollResult() {
    }

    public ScrollResult(List<BlogDTO> list, Long minTime, Integer offset) {
        this.list = list;
        this.minTime = minTime;
        this.offset = offset;
    }
}
