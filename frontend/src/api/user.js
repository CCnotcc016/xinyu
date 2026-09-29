// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
import request from '../utils/request'

export const getMe = () => request.get('/user/me')
export const updateProfile = (data) => request.put('/user/me', data)
export const changePassword = (data) => request.put('/user/password', data)
export const changePhone = (data) => request.put('/user/phone', data)
export const uploadAvatar = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/user/avatar', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
