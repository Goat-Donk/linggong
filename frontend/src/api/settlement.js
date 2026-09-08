import request from '@/utils/request'

// 岗位结算相关接口（对照后端 SettlementController，仅雇主可用）

// 查看岗位结算预览（未结算）或结果（已结算）
// data: { jobId, jobName, salary, frozenAmount, hiredCount, settled, triggerType,
//         grossWage, serviceFee, refundAmount, settleTime, items: [{workerId, workerName, workerIcon, halfDays, wageAmount}] }
export function getSettlementDetail(jobId) {
  return request.get('/settlement/detail', { params: { jobId } })
}

// 手动提前结算岗位（任意时刻，幂等）
export function settleJob(jobId) {
  return request.post('/settlement/settle', null, { params: { jobId } })
}
