import request from '@/utils/request'

// 钱包相关接口（对照后端 WalletController，登录即可用，双角色共用）

// 查看我的钱包（首次自动开户），data: { id, userId, balance }
export function getMyWallet() {
  return request.get('/wallet/me')
}

// 模拟充值（元），成功后 data 为最新钱包 { balance }
export function rechargeWallet(amount) {
  return request.post('/wallet/recharge', null, { params: { amount } })
}

// 模拟提现（元），成功后 data 为最新钱包 { balance }
export function withdrawWallet(amount) {
  return request.post('/wallet/withdraw', null, { params: { amount } })
}

// 我的钱包流水（分页，按时间倒序），data 为 WalletLogDTO[]：type/amount/balanceAfter/bizId/remark/createTime
export function getWalletLogs(page, pageSize) {
  return request.get('/wallet/logs', { params: { page, pageSize } })
}

// 我的钱包汇总（打工人收入统计卡）：data { balance, totalIncome, totalWithdraw, monthIncome }
export function getWalletSummary() {
  return request.get('/wallet/summary')
}
