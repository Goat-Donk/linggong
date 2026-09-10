import request from '@/utils/request'

// AI 优化岗位描述（仅雇主）。payload: { name, categoryName, salary, headcount, startTime, endTime, address, description }
// description 可空：空则 AI 根据表单字段生成，非空则润色。LLM 生成耗时可能超过默认 10s，单独放宽超时。
export function optimizeDescription(payload) {
  return request.post('/ai/job-description/optimize', payload, { timeout: 60000 })
}
