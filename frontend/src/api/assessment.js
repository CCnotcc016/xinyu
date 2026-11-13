import request from '../utils/request'

/** 量表定义（题目、选项、免责声明都由后端下发） */
export const listScales = () => request.get('/assessments/scales')
/** 提交作答：{ scaleKey, answers: [0,1,...] } */
export const submitAssessment = (data) => request.post('/assessments', data, { silent: true })
/** 历史记录，最新的在前 */
export const listAssessments = (limit = 10) => request.get('/assessments', { params: { limit } })
export const getAssessment = (id) => request.get(`/assessments/${id}`)
/** 重新生成 AI 解读（上次模型不可用时用） */
export const reInterpret = (id) => request.post(`/assessments/${id}/interpretation`)
export const deleteAssessment = (id) => request.delete(`/assessments/${id}`)
