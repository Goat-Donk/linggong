import request from '@/utils/request'

// 报名岗位（Lua 秒杀 + MQ 异步落单），需登录，成功 data 为报名单号
export function applyJob(jobId) {
  return request.post(`/job-application/${jobId}`)
}
