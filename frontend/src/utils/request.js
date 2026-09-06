import axios from 'axios'
import { showToast } from 'vant'

// 统一 axios 实例：baseURL = /api，开发期由 Vite 代理到后端 8080（生产期由 nginx 反代）
const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：自动带 token（后端约定 authorization 头放「裸 token」，无 Bearer 前缀）
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.authorization = token
  }
  return config
})

// 响应拦截器：统一处理后端 Result { success, data, errorMsg, total }
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 业务失败：HTTP 200 但 success=false，Toast 提示并中断
    if (res && res.success === false) {
      showToast(res.errorMsg || '操作失败')
      return Promise.reject(new Error(res.errorMsg || '操作失败'))
    }
    // 成功：把整个 Result 交给调用方（调用方按需取 res.data / res.total）
    return res
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      // 未登录/登录过期：清 token，跳登录页
      localStorage.removeItem('token')
      showToast('请先登录')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    } else {
      showToast('网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
