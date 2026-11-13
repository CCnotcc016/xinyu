import request from '../utils/request'

export const listConversations = () => request.get('/chat/conversations')
export const getChatUnread = () => request.get('/chat/unread-count')
export const listMessages = (peerId, beforeId, size = 30) =>
  request.get(`/chat/${peerId}/messages`, { params: beforeId ? { beforeId, size } : { size } })
export const sendMessage = (peerId, content) => request.post(`/chat/${peerId}/messages`, { content })
export const markChatRead = (peerId) => request.put(`/chat/${peerId}/read`)
