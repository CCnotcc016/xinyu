import request from '../utils/request'

// 保存失败由页面自己解释（离线时会改成「已离线保存」），所以不要那层通用提示
export const createDiary = (data) => request.post('/diaries', data, { silent: true })
export const listDiaries = (params) => request.get('/diaries', { params })
export const getDiary = (id) => request.get(`/diaries/${id}`)
export const updateDiary = (id, data) => request.put(`/diaries/${id}`, data)
export const deleteDiary = (id) => request.delete(`/diaries/${id}`)
export const getTrend = (range, end) =>
  request.get('/diaries/stats/trend', { params: end ? { range, end } : { range } })
export const getDistribution = () => request.get('/diaries/stats/distribution')
/** 情绪洞察：天气 / 场景 / 标签 / 星期与情绪分的关系 */
export const getInsights = () => request.get('/diaries/stats/insights')
/** 情绪日历热力图：days 天内的每日均分与记录条数 */
export const getCalendar = (days = 91) => request.get('/diaries/stats/calendar', { params: { days } })
/** AI 情绪周报：weekStart 传任意一天，服务端归一到那周的周一；refresh=true 强制重算 */
export const getWeeklyReport = (weekStart, refresh = false) =>
  request.get('/diaries/stats/weekly-report', {
    params: refresh ? { weekStart, refresh } : { weekStart }
  })
/** 月度 / 年度回顾：period=month|year，anchor 传该周期内任意一天 */
export const getRecap = (period = 'month', anchor) =>
  request.get('/diaries/stats/recap', { params: anchor ? { period, anchor } : { period } })
/** 回收站：7 天内删除的日记 */
export const listTrash = () => request.get('/diaries/trash')
export const restoreDiary = (id) => request.post(`/diaries/${id}/restore`)
/** 从回收站彻底删除，不可恢复 */
export const purgeDiary = (id) => request.delete(`/diaries/trash/${id}`)
