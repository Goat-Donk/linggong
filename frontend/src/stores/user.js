import { reactive } from 'vue'

// 轻量用户状态：教学项目不引 Pinia，用 reactive 单例 + localStorage 持久化。
// 后续状态复杂（跨页共享变多）再考虑引入 Pinia。
export const userState = reactive({
  token: localStorage.getItem('token') || '',
  // 用户信息（登录后存一份，首页/个人中心直接用昵称、角色，避免反复请求 /user/me）
  info: JSON.parse(localStorage.getItem('userInfo') || 'null')
})

export function setToken(token) {
  userState.token = token
  localStorage.setItem('token', token)
}

export function setInfo(info) {
  userState.info = info
  localStorage.setItem('userInfo', JSON.stringify(info))
}

export function clearUser() {
  userState.token = ''
  userState.info = null
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
}
