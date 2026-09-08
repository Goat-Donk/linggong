import request from '@/utils/request'

// 求职登记相关接口（对照后端 WorkerProfileController）

// 保存我的求职登记（不存在则插入，存在则覆盖），需登录，返回空
export function saveWorkerProfile(form) {
  return request.put('/worker-profile', form)
}

// 查看某用户求职登记主页（自己/雇主查看报名人都可用，他人手机号脱敏）
// data 为 WorkerProfileViewDTO：基础信息 + hasProfile + 登记内容 + isSelf
export function getWorkerProfile(userId) {
  return request.get(`/worker-profile/view/${userId}`)
}
