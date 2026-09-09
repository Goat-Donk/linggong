import request from '@/utils/request'

// 雇主拉黑打工人接口（对照后端 EmployerBlacklistController，均需雇主登录）

// 我的黑名单（分页），data 为 EmployerBlacklistDTO[]（workerId/workerName/reason/createTime）
export function getBlacklist(page, pageSize) {
  return request.get('/employer-blacklist', { params: { page, pageSize } })
}

// 拉黑一名打工人（仅雇主），body: { workerId, reason }
export function addBlacklist(workerId, reason) {
  return request.post('/employer-blacklist', { workerId, reason })
}

// 解除拉黑（仅雇主），解除后该工人可再次报名
export function removeBlacklist(workerId) {
  return request.delete(`/employer-blacklist/${workerId}`)
}
