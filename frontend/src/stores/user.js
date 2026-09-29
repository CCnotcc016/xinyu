// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('xinyu_token') || '',
    user: JSON.parse(localStorage.getItem('xinyu_user') || 'null')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN'
  },
  actions: {
    setLogin(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem('xinyu_token', token)
      localStorage.setItem('xinyu_user', JSON.stringify(user))
    },
    setUser(user) {
      this.user = user
      localStorage.setItem('xinyu_user', JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('xinyu_token')
      localStorage.removeItem('xinyu_user')
    }
  }
})
