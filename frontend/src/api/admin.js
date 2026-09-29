// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
import request from '../utils/request'

export const listUsers = (params) => request.get('/admin/users', { params })
export const updateUserStatus = (id, status) => request.put(`/admin/users/${id}/status`, null, { params: { status } })
export const deleteUser = (id) => request.delete(`/admin/users/${id}`)
export const listDiaries = (params) => request.get('/admin/diaries', { params })
export const deleteDiary = (id) => request.delete(`/admin/diaries/${id}`)
export const listPosts = (params) => request.get('/admin/posts', { params })
export const updatePostStatus = (id, status) => request.put(`/admin/posts/${id}/status`, null, { params: { status } })
export const deletePost = (id) => request.delete(`/admin/posts/${id}`)
export const listComments = (params) => request.get('/admin/comments', { params })
export const updateCommentStatus = (id, status) => request.put(`/admin/comments/${id}/status`, null, { params: { status } })
export const listReports = (params) => request.get('/admin/reports', { params })
export const handleReport = (id, action) => request.put(`/admin/reports/${id}`, null, { params: { action } })
export const listCrisisAlerts = (params) => request.get('/admin/crisis-alerts', { params })
export const getCrisisPendingCount = () => request.get('/admin/crisis-alerts/pending-count')
export const handleCrisisAlert = (id, data) => request.put(`/admin/crisis-alerts/${id}`, data)
