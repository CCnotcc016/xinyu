import request from '../utils/request'

export const listNotifications = (params) => request.get('/notifications', { params })
export const getNotificationUnread = () => request.get('/notifications/unread-count')
export const readNotification = (id) => request.post(`/notifications/${id}/read`)
export const readAllNotifications = () => request.post('/notifications/read-all')
