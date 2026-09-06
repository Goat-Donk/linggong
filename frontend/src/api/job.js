import request from '@/utils/request'

// 按分类分页查询上架岗位
export function getJobsByCategory(categoryId, page, pageSize) {
  return request.get(`/job/category/${categoryId}`, { params: { page, pageSize } })
}

// 附近岗位搜索（Redis GEO，按距离升序），params: { categoryId, x, y, radius, page, pageSize }
export function getNearbyJobs(params) {
  return request.get('/job/nearby', { params })
}
