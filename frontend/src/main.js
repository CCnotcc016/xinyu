// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import App from './App.vue'
import router from './router'
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

window.addEventListener('unhandledrejection', (event) => {
  if (event.reason?.handled) {
    event.preventDefault()
  }
})
