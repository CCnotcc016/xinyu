import { defineStore } from 'pinia'
import { getChatUnread } from '../api/chat'
import { getPendingRequestCount } from '../api/friend'
import { getNotificationUnread } from '../api/notification'
import { useUserStore } from './user'

/**
 * 聊天与好友申请的角标状态。
 *
 * 没有 websocket：底部导航每隔一段时间拉一次未读数就够了（H5 上省电也省连接）。
 * 客服账号不参与社交，任何刷新都会被直接跳过。
 */
export const useChatStore = defineStore('chat', {
  state: () => ({
    unread: 0,
    pendingRequests: 0,
    // 站内通知未读数，和上面两个一起轮询，避免多开一个定时器
    notifications: 0,
    timer: null
  }),
  actions: {
    async refresh() {
      const userStore = useUserStore()
      if (!userStore.token || userStore.isAdminOrStaff) {
        this.unread = 0
        this.pendingRequests = 0
        this.notifications = 0
        return
      }
      try {
        const [unread, pending, notices] = await Promise.all([
          getChatUnread(),
          getPendingRequestCount(),
          getNotificationUnread()
        ])
        this.unread = unread?.count || 0
        this.pendingRequests = pending?.count || 0
        this.notifications = notices?.count || 0
      } catch (e) {
        // 角标是锦上添花，拉失败就保持原样，不打扰用户
      }
    },
    startPolling(intervalMs = 30000) {
      this.stopPolling()
      this.refresh()
      this.timer = setInterval(() => this.refresh(), intervalMs)
    },
    stopPolling() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    },
    /** 打开会话读完消息后立刻把角标减掉，不用等下一次轮询 */
    setUnread(count) {
      this.unread = Math.max(0, count)
    }
  }
})
