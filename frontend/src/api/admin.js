import request from '../utils/request'

export const listUsers = (params) => request.get('/admin/users', { params })
export const updateUserStatus = (id, status) => request.put(`/admin/users/${id}/status`, null, { params: { status } })
export const deleteUser = (id) => request.delete(`/admin/users/${id}`)
/** 解除因密码连续错误产生的临时锁定 */
export const unlockUser = (id) => request.post(`/admin/users/${id}/unlock`)
export const listDiaries = (params) => request.get('/admin/diaries', { params })
export const deleteDiary = (id) => request.delete(`/admin/diaries/${id}`)
export const listPosts = (params) => request.get('/admin/posts', { params })
export const updatePostStatus = (id, status) => request.put(`/admin/posts/${id}/status`, null, { params: { status } })
export const deletePost = (id) => request.delete(`/admin/posts/${id}`)
export const listComments = (params) => request.get('/admin/comments', { params })
export const updateCommentStatus = (id, status) => request.put(`/admin/comments/${id}/status`, null, { params: { status } })
export const listReports = (params) => request.get('/admin/reports', { params })
export const getReportPendingCount = () => request.get('/admin/reports/pending-count')
export const handleReport = (id, action, remark) =>
  request.put(`/admin/reports/${id}`, null, { params: remark ? { action, remark } : { action } })
export const listCrisisAlerts = (params) => request.get('/admin/crisis-alerts', { params })
export const getCrisisPendingCount = () => request.get('/admin/crisis-alerts/pending-count')
export const handleCrisisAlert = (id, data) => request.put(`/admin/crisis-alerts/${id}`, data)
/** 回访：已跟进过的预警补充一次回访记录 */
export const followUpCrisisAlert = (id, note) => request.put(`/admin/crisis-alerts/${id}/follow-up`, { note })
// 敏感词库
export const listSensitiveWords = (params) => request.get('/admin/sensitive-words', { params })
export const addSensitiveWord = (word) =>
  request.post('/admin/sensitive-words', null, { params: { word } })
export const deleteSensitiveWord = (id) => request.delete(`/admin/sensitive-words/${id}`)
