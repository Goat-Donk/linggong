import request from '@/utils/request'

// 用户相关接口（对照后端 UserController）

// 发送登录验证码（存 Redis，2 分钟有效）
export function sendCode(phone) {
  return request.post('/user/code', null, { params: { phone } })
}

// 手机号 + 验证码登录，成功返回 token（在 data 里）
export function login(phone, code) {
  return request.post('/user/login', { phone, code })
}

// 获取当前登录用户（data 为 UserDTO：id/nickName/icon/role）
export function getMe() {
  return request.get('/user/me')
}

// 查看他人主页（data 为 UserDTO：id/nickName/icon/role，需登录）
export function getUserById(id) {
  return request.get(`/user/${id}`)
}

// 退出登录（后端删 token）
export function logout() {
  return request.post('/user/logout')
}

// 修改个人资料（字段均可选，null 不更新），返回空
export function updateProfile(form) {
  return request.put('/user/update', form)
}

// 查看他人资料（简介/年龄/性别/信用分），data 为 UserInfo 或 null（未填）
export function getUserInfo(id) {
  return request.get(`/user/info/${id}`)
}
