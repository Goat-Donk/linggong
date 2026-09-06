import request from '@/utils/request'

// 按分类分页查询上架岗位
export function getJobsByCategory(categoryId, page, pageSize) {
  return request.get(`/job/category/${categoryId}`, { params: { page, pageSize } })
}

// 附近岗位搜索（Redis GEO，按距离升序），params: { categoryId, x, y, radius, page, pageSize }
export function getNearbyJobs(params) {
  return request.get('/job/nearby', { params })
}

// 岗位详情
export function getJobById(id) {
  return request.get(`/job/${id}`)
}

// 发布岗位（仅雇主 role=1），成功 data 为岗位 id
// form: { categoryId, name, address, x, y, salary, headcount, startTime, endTime, description }
export function publishJob(form) {
  return request.post('/job', form)
}
