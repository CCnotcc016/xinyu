// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
import request from '../utils/request'

export const listPosts = (params) => request.get('/treehole/posts', { params })
export const createPost = (data) => request.post('/treehole/posts', data)
export const getPost = (id) => request.get(`/treehole/posts/${id}`)
export const likePost = (id) => request.post(`/treehole/posts/${id}/like`)
export const unlikePost = (id) => request.delete(`/treehole/posts/${id}/like`)
export const listComments = (id, params) => request.get(`/treehole/posts/${id}/comments`, { params })
export const addComment = (id, data) => request.post(`/treehole/posts/${id}/comments`, data)
export const createReport = (data) => request.post('/reports', data)
