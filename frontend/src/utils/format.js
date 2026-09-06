// 展示层格式化工具（首页/详情/列表多处复用）

// 距离（米）→ 人类可读：<1m / 850m / 1.2km
export function formatDistance(m) {
  if (m == null) return ''
  if (m < 1) return '<1m'
  if (m < 1000) return `${Math.round(m)}m`
  return `${(m / 1000).toFixed(1)}km`
}

// 薪资（元）→ ¥120，空则「面议」
export function formatSalary(salary) {
  if (salary == null) return '面议'
  return `¥${salary}`
}

// 起止时间 → "09-06 ~ 09-08"（只取日期部分，空值兜底）
export function formatDateRange(start, end) {
  const fmt = (s) => {
    if (!s) return ''
    const date = String(s).split(/[T ]/)[0] // "2026-09-06T10:00:00" → "2026-09-06"
    return date && date.length >= 10 ? date.slice(5) : date // → "09-06"
  }
  const s = fmt(start)
  const e = fmt(end)
  if (s && e && s !== e) return `${s} ~ ${e}`
  return s || e || ''
}

// 报名状态文案：0 待确认 / 1 已录用 / 2 已完成 / 3 已取消
export function formatApplyStatus(status) {
  return { 0: '待确认', 1: '已录用', 2: '已完成', 3: '已取消' }[status] ?? '未知'
}

// 报名状态对应 Vant Tag 颜色类型
export function applyStatusType(status) {
  return { 0: 'warning', 1: 'success', 2: 'primary', 3: 'default' }[status] ?? 'default'
}

// 完整时间 "2026-09-06T10:00:00" → "2026-09-06 10:00"
export function formatDateTime(s) {
  if (!s) return ''
  return String(s).replace('T', ' ').slice(0, 16)
}
