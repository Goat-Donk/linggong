import request from '@/utils/request'

// 收藏 / 取消收藏岗位（需登录），isFavorite: true 收藏 / false 取消
export function favoriteJob(jobId, isFavorite) {
  return request.put(`/job-favorite/${jobId}/${isFavorite}`)
}

// 是否已收藏该岗位（需登录），data 为 boolean
export function isFavorited(jobId) {
  return request.get(`/job-favorite/or/not/${jobId}`)
}

// 我的收藏分页列表（需登录），data 为 JobFavoriteDTO[]：jobId/jobName/address/salary/headcount/status/createTime
export function getMyFavorites(page, pageSize) {
  return request.get('/job-favorite/my', { params: { page, pageSize } })
}
