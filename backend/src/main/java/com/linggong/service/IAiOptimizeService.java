package com.linggong.service;

import com.linggong.dto.AiOptimizeRequest;
import com.linggong.dto.Result;

/**
 * AI 能力服务：岗位描述优化。
 */
public interface IAiOptimizeService {

    /**
     * 优化 / 生成岗位描述（仅雇主可用）。
     * 草稿为空则根据表单字段生成，非空则润色补全；异常或未配置 Key 一律 fail-soft 返回友好提示。
     */
    Result optimizeJobDescription(AiOptimizeRequest request);
}
