import request from '../utils/request'

export const getMe = () => request.get('/user/me')
/** 别人的公开主页（从树洞昵称点进来）；relation 决定显示「加好友 / 发消息 / 已是好友」 */
export const getUserProfile = (id) => request.get(`/user/${id}/profile`)
/** 某人公开的树洞帖子（匿名帖不返回） */
export const getUserPosts = (id, params) => request.get(`/user/${id}/posts`, { params })
export const updateProfile = (data) => request.put('/user/me', data)
export const changePassword = (data) => request.put('/user/password', data)
export const changePhone = (data) => request.put('/user/phone', data)
/** 确认同意现行《用户协议》《隐私政策》，只有版本号与服务端一致才会被接受 */
export const acceptAgreement = (version) => request.post('/user/agreement', { version })
/** 定位与天气授权开关，可随时撤回 */
export const updateLocationConsent = (consent) => request.put('/user/location-consent', { consent })
export const uploadAvatar = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/user/avatar', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
/** 导出账号数据：返回一个可直接存成 .json 的对象 */
export const exportMyData = () => request.get('/user/data-export')
/** 注销账号：密码二次确认，成功后数据不可恢复 */
export const deleteMyAccount = (password) => request.delete('/user/account', { data: { password } })
