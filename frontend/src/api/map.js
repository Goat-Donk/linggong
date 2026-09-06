import request from '@/utils/request'

// 逆地理编码：经纬度 → 文字地址（后端代理高德，需登录）
export function regeo(x, y) {
  return request.get('/map/regeo', { params: { x, y } })
}
