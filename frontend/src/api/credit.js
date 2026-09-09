import request from '@/utils/request'

// 信用相关接口（对照后端 CreditController）

// 我的当前信用分（data 为数字 0~100）
export function getMyCredit() {
  return request.get('/credit/my')
}

// 我的信用变动流水（分页，按时间倒序）
// data 为 CreditLog[]：reasonType/changeAmount/afterCredit/bizId/remark/createTime
export function getCreditLogs(page, pageSize) {
  return request.get('/credit/logs', { params: { page, pageSize } })
}
