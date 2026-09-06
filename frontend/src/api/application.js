import request from '@/utils/request'

// 报名岗位（Lua 秒杀 + MQ 异步落单），需登录，成功 data 为报名单号
export function applyJob(jobId) {
  return request.post(`/job-application/${jobId}`)
}

// 我的报名记录（分页），data 为 JobApplicationDTO[]，total 为总数
export function getMyApplications(page, pageSize) {
  return request.get('/job-application/my', { params: { page, pageSize } })
}
