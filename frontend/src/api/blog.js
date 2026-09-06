import request from '@/utils/request'

// 动态相关接口（对照后端 BlogController）

// 关注的人动态（滚动分页），data 为 ScrollResult { list, minTime, offset }
// 首次不传 lastId、offset 为 0；后续回传上一页返回的 minTime / offset
export function queryBlogOfFollow({ lastId, offset = 0, pageSize = 5 } = {}) {
  const params = { offset, pageSize }
  if (lastId != null) params.lastId = lastId
  return request.get('/blog/of/follow', { params })
}

// 点赞 / 取消点赞（幂等切换），返回空
export function likeBlog(id) {
  return request.put(`/blog/like/${id}`)
}
