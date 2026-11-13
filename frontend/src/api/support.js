import request from '../utils/request'

// 用户侧：与管理员 / 客服的单条会话
export const getThread = () => request.get('/support/thread')
export const sendMessage = (content) => request.post('/support/messages', { content })
export const getUnreadCount = () => request.get('/support/unread-count')

// 客服工作台（ADMIN 与 STAFF 都可访问）
export const listConversations = (params) => request.get('/staff/conversations', { params })
export const getConversation = (id) => request.get(`/staff/conversations/${id}`)
export const staffSendMessage = (id, content) => request.post(`/staff/conversations/${id}/messages`, { content })
// 结单：把处理完的会话标记为结束，返回刷新后的会话
export const closeConversation = (id) => request.put(`/staff/conversations/${id}/close`)
export const getStaffUnreadCount = () => request.get('/staff/unread-count')
