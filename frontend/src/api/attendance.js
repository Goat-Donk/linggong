import request from '@/utils/request'

// 每日考勤相关接口（对照后端 AttendanceController）

// 打工人发起今日到岗（仅打工人，当天）
export function punchOn(jobId) {
  return request.post('/attendance/on', null, { params: { jobId } })
}

// 打工人请求今日下工（需到岗已通过）
export function punchOff(jobId) {
  return request.post('/attendance/off', null, { params: { jobId } })
}

// 我的考勤：岗位 + 今天可打卡状态(canOn/canOff/hint) + 逐日记录(records)
export function getMyAttendance(jobId) {
  return request.get('/attendance/my', { params: { jobId } })
}

// 雇主考勤核销可选岗位列表（data: { id, name, salary, startTime, endTime, hiredCount }[]）
export function getAttendanceJobs() {
  return request.get('/attendance/jobs')
}

// 雇主查看某岗位某日全部已录用工人考勤（date 空则今天）
export function getJobAttendance(jobId, date) {
  return request.get('/attendance/job', { params: { jobId, date } })
}

// 雇主核销一次打卡申请（on/off 通过或驳回）
// body: { jobId, workerId, workDate, punch: 'on'|'off', pass: boolean }
export function auditAttendance(body) {
  return request.post('/attendance/audit', body)
}

// 雇主补记某工人今日完工（置到岗+下工通过，仅限今天）
// body: { jobId, workerId, workDate }
export function confirmAttendanceOff(body) {
  return request.post('/attendance/confirm-off', body)
}
