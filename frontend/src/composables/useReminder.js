import { ref } from 'vue'
import { showConfirmDialog } from 'vant'

/**
 * 写日记提醒。
 *
 * 纯前端实现：网页关掉之后浏览器不会再叫醒自己，所以只在「打开着心语」时提醒 ——
 * 每天到点检查一次，当天提醒过就不再重复。时间与开关存在本机，不上传服务器。
 */
const KEY = 'xinyu:reminder'

function today() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function read() {
  try {
    const raw = JSON.parse(localStorage.getItem(KEY) || '{}')
    return {
      enabled: !!raw.enabled,
      time: /^\d{2}:\d{2}$/.test(raw.time || '') ? raw.time : '21:00',
      lastDate: raw.lastDate || ''
    }
  } catch {
    return { enabled: false, time: '21:00', lastDate: '' }
  }
}

function write(config) {
  localStorage.setItem(KEY, JSON.stringify(config))
}

/** 「21:00」→ 1260 分钟，方便和当前时刻比较 */
function toMinutes(time) {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}

export function useReminder() {
  const config = ref(read())

  function save(patch) {
    config.value = { ...config.value, ...patch }
    write(config.value)
  }

  function setEnabled(enabled) {
    save({ enabled })
  }

  function setTime(time) {
    save({ time })
  }

  /** 已到点、且今天还没提醒过 */
  function due() {
    const { enabled, time, lastDate } = config.value
    if (!enabled || lastDate === today()) return false
    const now = new Date()
    return now.getHours() * 60 + now.getMinutes() >= toMinutes(time)
  }

  function markReminded() {
    save({ lastDate: today() })
  }

  return { config, setEnabled, setTime, due, markReminded }
}

let timer = null
let asking = false

/**
 * 全局调度：每分钟看一眼是否到点。
 * 只在登录后启动，客服账号不提醒（他们进主页就是工作台）。
 */
export function startReminderScheduler({ shouldRun, onWrite }) {
  stopReminderScheduler()
  const check = async () => {
    if (asking || !shouldRun()) return
    const { due, markReminded } = useReminder()
    if (!due()) return
    asking = true
    markReminded()
    try {
      await showConfirmDialog({
        title: '今天还没写日记',
        message: '花三分钟写两句，AI 会给你一段回应。',
        confirmButtonText: '去写',
        cancelButtonText: '今天不写'
      })
      onWrite?.()
    } catch {
      // 用户选了「今天不写」，今天就不再打扰
    } finally {
      asking = false
    }
  }
  // 打开就开始判断，不用等满一分钟
  check()
  timer = setInterval(check, 60 * 1000)
}

export function stopReminderScheduler() {
  if (timer) clearInterval(timer)
  timer = null
  asking = false
}
