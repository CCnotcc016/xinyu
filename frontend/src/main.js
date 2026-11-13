import { Capacitor } from '@capacitor/core'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import App from './App.vue'
import router from './router'
import { setupNativeShell } from './native'
import './assets/main.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(Vant)

// 请求失败时 request.js 已经弹过 Toast，页面多数无需再 catch。
// 这里忽略这类「已处理」的错误，避免控制台出现无意义的 Vue 告警。
app.config.errorHandler = (err) => {
  if (!err?.handled) console.error(err)
}

app.mount('#app')

// 原生外壳（状态栏、返回键、启动图）只在 App 里生效，网页里这个函数直接 return
setupNativeShell()

window.addEventListener('unhandledrejection', (event) => {
  if (event.reason?.handled) {
    event.preventDefault()
  }
})

// PWA：只有正式构建才注册 Service Worker，开发环境注册会和 Vite 的热更新打架。
// 装进 App 后页面本来就在本地，再挂一层缓存反而会在升级 APK 后继续吐旧页面，所以跳过。
if (import.meta.env.PROD && !Capacitor.isNativePlatform() && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // 注册失败（例如不是 HTTPS）不影响正常使用，只是不能离线打开
    })
  })
}
