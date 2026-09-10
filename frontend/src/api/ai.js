import request from '@/utils/request'

// AI 优化岗位描述（仅雇主）。payload: { name, categoryName, salary, headcount, startTime, endTime, address, description }
// description 可空：空则 AI 根据表单字段生成，非空则润色。LLM 生成耗时可能超过默认 10s，单独放宽超时。
export function optimizeDescription(payload) {
  return request.post('/ai/job-description/optimize', payload, { timeout: 60000 })
}

// AI 问答助手 SSE 流式对话。返回原生 fetch 的 Response，由调用方用 ReadableStream 逐段读。
// axios 不适配 SSE，这里用原生 fetch；token 放 authorization 头（与 request.js 同约定）。
// 注意：后端限流 / 未登录时返回的是 Result JSON 而非流，调用方需按 content-type 分流处理。
export function chatAiAssistant(message, signal) {
  const token = localStorage.getItem('token') || ''
  return fetch(`/api/ai/assistant/chat?message=${encodeURIComponent(message)}`, {
    method: 'GET',
    headers: { authorization: token },
    signal
  })
}
