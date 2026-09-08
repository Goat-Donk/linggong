package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 求职登记入参（打工人保存/更新自己的登记）。
 *
 * <p>categoryIds / skillTags / workTime 为数组，入库时拼成逗号分隔字符串。
 * salaryMin / salaryMax 可空，表示期望日薪区间（元）。
 */
@Data
@Schema(description = "求职登记入参")
public class WorkerProfileFormDTO {

    /** 求职意向一句话 */
    @Schema(description = "求职意向一句话")
    private String title;

    /** 期望岗位分类 id（可多选） */
    @Schema(description = "期望岗位分类 id（可多选）")
    private List<Long> categoryIds;

    /** 技能标签 */
    @Schema(description = "技能标签")
    private List<String> skillTags;

    /** 期望日薪下限（元） */
    @Schema(description = "期望日薪下限（元）")
    private Integer salaryMin;

    /** 期望日薪上限（元） */
    @Schema(description = "期望日薪上限（元）")
    private Integer salaryMax;

    /** 可出勤时段 */
    @Schema(description = "可出勤时段（可多选）")
    private List<String> workTime;

    /** 常驻区域 / 可到岗地点 */
    @Schema(description = "常驻区域 / 可到岗地点")
    private String location;
}
