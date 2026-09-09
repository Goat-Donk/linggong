import request from '@/utils/request'

// 按分类分页查询上架岗位
export function getJobsByCategory(categoryId, page, pageSize) {
  return request.get(`/job/category/${categoryId}`, { params: { page, pageSize } })
}

// 附近岗位搜索（Redis GEO，按距离升序），params: { categoryId, x, y, radius, page, pageSize }
export function getNearbyJobs(params) {
  return request.get('/job/nearby', { params })
}

// 统一岗位列表查询（关键词搜索 + 分类 + 薪资/距离筛选 + 排序 + 分页）
// params: { keyword, categoryId, minSalary, maxSalary, x, y, maxDistance, sort, page, pageSize }
// sort: latest 最新 / salary 薪资最高 / distance 距离最近（距离相关需同时传 x/y）
export function getJobList(params) {
  return request.get('/job/list', { params })
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

// 我的岗位（雇主，分页），data 为 JobMyDTO[]（含已录用/待确认人数、结算状态）
export function getMyJobs(page, pageSize) {
  return request.get('/job/my', { params: { page, pageSize } })
}

// 编辑岗位（仅发布者本人），form 同发布
export function updateJob(id, form) {
  return request.put(`/job/${id}`, form)
}

// 下架岗位（仅发布者本人）
export function offShelfJob(id) {
  return request.put(`/job/off/${id}`)
}
