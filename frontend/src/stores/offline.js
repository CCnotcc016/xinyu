import { defineStore } from 'pinia'
import { onNetworkChange } from '../offline/bus'
import { countOutbox } from '../offline/outbox'
import { flushOutbox } from '../offline/sync'
import { useUserStore } from './user'

// 定时器和事件解绑函数放模块作用域：放进 state 会变成响应式对象，pinia 也会报警告
let retryTimer = null
let offBus = null
let listening = false
const RETRY_INTERVAL = 20000

/**
 * 离线状态与待同步队列的界面侧状态。
 *
 * 「在线」的判定以真实接口为准，而不是 navigator.onLine ——
 * 手机连着 Wi-Fi、后端电脑关机时，系统认为在线，但接口一个都通不了，
 * 用户看到「网络正常」会更困惑。所以只有请求真的连上了才算在线。
 */
export const useOfflineStore = defineStore('offline', {
  state: () => ({
    online: navigator.onLine !== false,
    pending: 0,
    syncing: false,
    lastSyncAt: null,
    // 本地 id → 服务器 id，详情页据此从「待同步」切到正式的那篇
    syncedMap: {},
    lastError: ''
  }),
  getters: {
    /** 底部提示条：离线或还有未同步内容时才出现 */
    showBanner: (state) => !state.online || state.pending > 0 || state.syncing
  },
  actions: {
    async init() {
      if (listening) {
        await this.refreshPending()
        return
      }
      listening = true
      this.online = navigator.onLine !== false
      offBus = onNetworkChange((reachable) => {
        const wasOffline = !this.online
        this.online = reachable
        if (!reachable) return
        // 刚恢复：把之前没传上去的补上
        if (wasOffline) this.sync()
      })
      window.addEventListener('online', this.handleSystemOnline)
      window.addEventListener('offline', this.handleSystemOffline)
      document.addEventListener('visibilitychange', this.handleVisible)
      retryTimer = setInterval(() => {
        if (this.pending > 0 && !this.syncing) this.sync()
      }, RETRY_INTERVAL)
      await this.refreshPending()
      if (this.online) this.sync()
    },

    dispose() {
      if (offBus) offBus()
      offBus = null
      window.removeEventListener('online', this.handleSystemOnline)
      window.removeEventListener('offline', this.handleSystemOffline)
      document.removeEventListener('visibilitychange', this.handleVisible)
      clearInterval(retryTimer)
      retryTimer = null
      listening = false
    },

    /** 退出登录：队列归属于账号，界面上的计数和映射也要跟着清掉 */
    reset() {
      this.dispose()
      this.pending = 0
      this.syncing = false
      this.syncedMap = {}
      this.lastError = ''
      this.online = navigator.onLine !== false
    },

    handleSystemOnline() {
      // 系统说网回来了不代表后端可达，交给 sync() 去试，试通了才算在线
      this.sync()
    },

    handleSystemOffline() {
      this.online = false
    },

    handleVisible() {
      if (document.visibilityState !== 'visible') return
      this.refreshPending()
      if (this.pending > 0 && !this.syncing) this.sync()
    },

    async refreshPending() {
      const userStore = useUserStore()
      if (!userStore.user?.id) {
        this.pending = 0
        return 0
      }
      this.pending = await countOutbox(userStore.user.id)
      return this.pending
    },

    /**
     * 补传待同步日记。自动重试与用户手动点「立即同步」走的是同一条路，
     * 差别只在界面怎么提示（由调用方决定），这里保持静默。
     */
    async sync() {
      const userStore = useUserStore()
      if (this.syncing || !userStore.token || !userStore.user?.id) return null
      await this.refreshPending()
      if (!this.pending) return null
      this.syncing = true
      try {
        const result = await flushOutbox(userStore.user.id)
        this.pending = result.remaining
        this.online = !result.offline
        this.lastError = result.offline ? '网络不可用，稍后会自动重试' : ''
        if (result.synced > 0) {
          this.lastSyncAt = Date.now()
          this.syncedMap = { ...this.syncedMap, ...result.mapping }
          // 列表页 / 首页监听这个事件后重新拉一次，把「待同步」换成正式记录
          window.dispatchEvent(new CustomEvent('xinyu:synced', { detail: result }))
        }
        return result
      } finally {
        this.syncing = false
      }
    }
  }
})
