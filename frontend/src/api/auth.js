import request from '../utils/request'

export const register = (data) => request.post('/auth/register', data)
// 登录不该让人等 30 秒：后端没开时 12 秒内失败，登录页才好顺势转成离线登录
export const login = (data) => request.post('/auth/login', data, { timeout: 12000 })
export const getMe = () => request.get('/user/me')
export const getCaptcha = () => request.get('/auth/captcha')
export const sendSmsCode = (data) => request.post('/auth/sms-code', data)
