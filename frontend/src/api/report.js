import request from '../utils/request'

/** 提交举报：targetType = POST / COMMENT / USER */
export const createReport = (data) => request.post('/reports', data)

/** 我发起的举报：带处理状态与处理说明，用户能看到结果 */
export const listMyReports = (params) => request.get('/reports/mine', { params })
