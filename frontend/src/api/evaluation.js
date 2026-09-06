import request from '@/utils/request'

// 互评相关接口（对照后端 EvaluationController）

// 岗位下的评价列表（分页），data 为 EvaluationDTO[]，total 为总数
export function getEvaluationsByJob(jobId, page, pageSize) {
  return request.get(`/evaluation/job/${jobId}`, { params: { page, pageSize } })
}

// 发布互评（form: { jobId, toUserId, rating, content }），成功 data 为评价 id
export function publishEvaluation(form) {
  return request.post('/evaluation', form)
}
