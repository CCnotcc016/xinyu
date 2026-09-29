// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
import request from '../utils/request'

export const createDiary = (data) => request.post('/diaries', data)
export const listDiaries = (params) => request.get('/diaries', { params })
export const getDiary = (id) => request.get(`/diaries/${id}`)
export const updateDiary = (id, data) => request.put(`/diaries/${id}`, data)
export const deleteDiary = (id) => request.delete(`/diaries/${id}`)
export const getTrend = (range) => request.get('/diaries/stats/trend', { params: { range } })
export const getDistribution = () => request.get('/diaries/stats/distribution')
