package com.linggong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linggong.dto.EvaluationFormDTO;
import com.linggong.dto.Result;
import com.linggong.entity.JobEvaluation;

/**
 * 互评服务接口：发布互评、查看岗位评价列表。
 */
public interface IJobEvaluationService extends IService<JobEvaluation> {

    /**
     * 发布互评（雇主评工人 / 工人评雇主）。
     */
    Result publish(EvaluationFormDTO form);

    /**
     * 查看岗位下的评价列表（分页）。
     */
    Result queryByJob(Long jobId, Integer page, Integer pageSize);
}
