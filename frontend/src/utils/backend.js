import axios from 'axios'

/**
 * 后端地址的唯一来源。
 *
 * 网页部署（nginx）时走同源相对路径 /api，由 nginx 反代到 8080；
 * 打包成 App 后页面来自 https://localhost，相对路径只会打到 WebView 自己身上，
 * 所以 .env.capacitor 里用 VITE_API_BASE 给一个绝对地址，构建时注入。
 *
 * 构建时写死的地址换台电脑、换个 Wi-Fi 就失效了（192.168.43.x → 192.168.1.x），
 * 但又不能为了改一行 IP 重新打包 + 卸载重装，所以在设置页留了一个运行时覆盖：
 * 覆盖值存在 localStorage，优先级高于构建注入的地址。
 */
const ENV_BASE = (import.meta.env.VITE_API_BASE || '/api').replace(/\/+$/, '')
const OVERRIDE_KEY = 'xinyu_api_base'

/** 允许用户只填 IP（192.168.1.14:8080），自动补协议和 /api 后缀 */
export function normalizeBase(raw) {
  let v = String(raw || '').trim()
  if (!v) return ENV_BASE
  // 同源相对路径（网页部署的 /api）不能补协议，否则会变成 http://api
  if (v.startsWith('/')) return v.replace(/\/+$/, '') || '/api'
  if (!/^https?:\/\//i.test(v)) v = `http://${v}`
  v = v.replace(/\/+$/, '')
  if (!/\/api$/i.test(v)) v += '/api'
  return v
}

function readOverride() {
  try {
    return localStorage.getItem(OVERRIDE_KEY) || ''
  } catch {
    return ''
  }
}

let currentBase = normalizeBase(readOverride() || ENV_BASE)

export function getApiBase() {
  return currentBase
}

/** 构建时注入的地址，用来「恢复默认」 */
export function defaultApiBase() {
  return ENV_BASE
}

// axios 实例在模块加载时就建好了，改地址得把它们手里的 baseURL 一起改掉
const clients = new Set()

export function registerClient(instance) {
  clients.add(instance)
  instance.defaults.baseURL = currentBase
  return instance
}

function applyBase(base) {
  currentBase = base
  clients.forEach((c) => {
    c.defaults.baseURL = base
  })
  probe.defaults.baseURL = base
  markBackendUp()
}

export function setApiBase(raw) {
  const base = normalizeBase(raw)
  try {
    localStorage.setItem(OVERRIDE_KEY, base)
  } catch {
    // 存不进去（无痕模式）也在本次会话内生效
  }
  applyBase(base)
  return base
}

export function resetApiBase() {
  try {
    localStorage.removeItem(OVERRIDE_KEY)
  } catch {
    // 忽略
  }
  applyBase(ENV_BASE)
  return ENV_BASE
}

/**
 * 后端返回的是 /uploads/xxx 这种站内路径，网页里直接用没问题，
 * App 里必须补上主机名，否则会去 https://localhost/uploads/xxx 找图片。
 * 地址可能在运行中被改，所以这里每次现算，不能提前固化成常量。
 */
export function mediaUrl(path) {
  const origin = /^https?:\/\//.test(currentBase) ? new URL(currentBase).origin : ''
  if (!path || !origin || !path.startsWith('/')) {
    return path
  }
  return origin + path
}

/* ---------------------------------------------------------------------------
 * 后端可达性：一次探测 + 一段「别再试了」的窗口
 *
 * 后端没启动时，每个请求都要熬满 30 秒超时才报「请求超时」，一进首页会同时
 * 挂住五六个请求，看起来就是「卡死」。所以探测失败后开一个短窗口，
 * 窗口内的请求立刻失败，交给页面已有的离线逻辑（缓存 / 待同步队列）去处理。
 * 窗口很短，后端一起来下一次请求就能探通，不会一直挡着。
 * ------------------------------------------------------------------------- */

/** 探测失败后，这段时间内不再真的发请求（毫秒） */
const DOWN_WINDOW = 15000

let downUntil = 0

export function markBackendDown(windowMs = DOWN_WINDOW) {
  downUntil = Math.max(downUntil, Date.now() + windowMs)
}

export function markBackendUp() {
  downUntil = 0
}

export function isBackendDown() {
  return downUntil > Date.now()
}

const probe = axios.create({ baseURL: currentBase, timeout: 3000 })

/**
 * 试一个还没生效的地址（设置页「测试并保存」用）。
 * 不碰静默窗口：试错不应该让当前会话的请求也跟着一起失败。
 */
export async function testApiBase(raw, timeout = 3000) {
  const base = normalizeBase(raw)
  try {
    const { data } = await axios.get(`${base}/auth/ping`, { timeout })
    return data?.code === 0
  } catch {
    return false
  }
}

/**
 * 问一次 /auth/ping。
 * 返回 true 表示后端活着；false 表示连不上（或 3 秒内没回应），并顺手开静默窗口。
 */
export async function probeBackend(timeout = 3000) {
  try {
    const { data } = await probe.get('/auth/ping', { timeout })
    if (data?.code === 0) {
      markBackendUp()
      return true
    }
    return false
  } catch {
    markBackendDown()
    return false
  }
}
