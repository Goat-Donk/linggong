import request from '@/utils/request'

// 关注相关接口（对照后端 FollowController）

// 关注 / 取关（isFollow: true 关注 / false 取关），返回空
export function follow(id, isFollow) {
  return request.put(`/follow/${id}/${isFollow}`)
}
