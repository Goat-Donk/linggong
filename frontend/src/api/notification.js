import request from '@/utils/request'

// 站内通知相关接口（对照后端 NotificationController，需登录）

// 我的通知列表（分页，按时间倒序），data 为 Notification[]，total 为总数
export function getNotifications(page, pageSize) {
  return request.get('/notification/list', { params: { page, pageSize } })
}

// 未读通知数，data 为数字
export function getUnreadCount() {
  return request.get('/notification/unread-count')
}

// 标记全部已读
export function markAllRead() {
  return request.put('/notification/read-all')
}
