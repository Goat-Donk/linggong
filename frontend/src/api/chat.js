import request from '@/utils/request'

// 站内沟通（聊天）接口（对照后端 ChatController，需登录）

// 定位会话（不存在返回 data=null，不自动建）
export function getConversation(jobId, peerId) {
  return request.get('/chat/conversation', { params: { jobId, peerId } })
}

// 我的会话列表，data 为 ConversationDTO[]，total 为总数
export function getConversations() {
  return request.get('/chat/conversations')
}

// 会话消息列表（分页倒序），data 为 ChatMessageDTO[]，total 为总数
export function getMessages(conversationId, page, pageSize) {
  return request.get('/chat/messages', { params: { conversationId, page, pageSize } })
}

// 发送消息，data 为会话 id
export function sendMessage(data) {
  return request.post('/chat/send', data)
}

// 未读消息总数，data 为数字
export function getUnreadCount() {
  return request.get('/chat/unread-count')
}
