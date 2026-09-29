// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
import axios from 'axios'
import { showToast } from 'vant'
import { useUserStore } from '../stores/user'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 0) {
      showToast(res.message || '请求失败')
      // 提示已在此处统一给出，标记为 handled，避免页面里未 catch 时产生
      // 未处理的 Promise 拒绝告警（见 main.js 的 unhandledrejection 处理）
      const error = new Error(res.message || '请求失败')
      // 保留业务错误码，页面需要区分具体原因时（如验证码答错要换图）才判断得了
      error.code = res.code
      return Promise.reject(markHandled(error))
    }
    return res.data
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      router.replace({ name: 'login' })
      showToast('请先登录')
    } else {
      showToast(error.response?.data?.message || '网络异常，请稍后再试')
    }
    return Promise.reject(markHandled(error))
  }
)

function markHandled(error) {
  if (error) error.handled = true
  return error
}

export default request
