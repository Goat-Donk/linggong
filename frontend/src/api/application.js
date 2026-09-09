import request from '@/utils/request'

// 报名岗位（Lua 秒杀 + MQ 异步落单），需登录，成功 data 为报名单号
export function applyJob(jobId) {
  return request.post(`/job-application/${jobId}`)
}

// 我的报名记录（分页），data 为 JobApplicationDTO[]，total 为总数
export function getMyApplications(page, pageSize) {
  return request.get('/job-application/my', { params: { page, pageSize } })
}

// 雇主视角：我发布岗位下的报名列表（分页），data 为 EmployerApplicationDTO[]（含报名人昵称头像）
export function getEmployerApplications(page, pageSize) {
  return request.get('/job-application/employer', { params: { page, pageSize } })
}

// 雇主通过报名（0 待确认 → 1 已录用）
export function approveApplication(id) {
  return request.put(`/job-application/${id}/approve`)
}

// 雇主拒绝报名（0 待确认 → 3 已取消）
export function rejectApplication(id) {
  return request.put(`/job-application/${id}/reject`)
}

// 打工人撤销报名（0 待确认 → 3 已取消）
export function cancelApplication(id) {
  return request.put(`/job-application/${id}/cancel`)
}

// 打工人放弃已录用岗位（1 → 3），释放名额回招
export function quitApplication(id) {
  return request.put(`/job-application/${id}/quit`)
}

// 雇主取消对某工人的录用（1 → 3），释放名额补招
export function dismissApplication(id) {
  return request.put(`/job-application/${id}/dismiss`)
}
