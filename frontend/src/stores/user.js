import { defineStore } from 'pinia'
import { clearApiCache } from '../offline/cache'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('xinyu_token') || '',
    user: JSON.parse(localStorage.getItem('xinyu_user') || 'null')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN',
    // 客服：只能看自己接手的会话；管理员能看全部
    isStaff: (state) => state.user?.role === 'STAFF',
    isAdminOrStaff: (state) => state.user?.role === 'ADMIN' || state.user?.role === 'STAFF'
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
      // 缓存里都是这个账号的日记、树洞，换人用这台手机不能留
      // 待同步队列不清：那是用户自己写下的东西，登录回原账号还要继续传
      clearApiCache()
    }
  }
})
