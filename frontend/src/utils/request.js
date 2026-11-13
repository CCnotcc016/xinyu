import axios from 'axios'
import { showToast } from 'vant'
import { useUserStore } from '../stores/user'
import { isBackendDown, markBackendDown, markBackendUp, registerClient } from './backend'
import { cacheKey, readCache, readLatestCache, writeCache } from '../offline/cache'
import { reportReachable, reportUnreachable } from '../offline/bus'
import router from '../router'

/**
 * 断网时仍值得拿旧数据顶上的接口。
 * 认证、后台管理不进缓存：前者带一次性验证码，后者管理员在电脑上处理，
 * 存进手机没有任何好处，反而多一份隐私面。
 */
const CACHEABLE_PREFIXES = [
  '/diaries',
  '/treehole',
  '/user',
  '/notifications',
  '/chat',
  '/friend',
  '/friends',
  '/reports',
  '/support',
  '/assessments'
]

function cacheable(config) {
  if ((config?.method || 'get').toLowerCase() !== 'get') return false
  const url = config?.url || ''
  return CACHEABLE_PREFIXES.some((prefix) => url.startsWith(prefix))
}

const request = registerClient(
  axios.create({
    timeout: 30000
  })
)

request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  // 刚确认过后端连不上：直接失败，交给缓存 / 待同步队列兜底。
  // 这样「没开后端」时页面能立刻出内容，而不是每个请求各熬 30 秒超时。
  if (isBackendDown()) {
    return Promise.reject(fastFail(config))
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    markBackendUp()
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
    reportReachable()
    // 顺手存一份：断网时这个接口能拿它顶上（不阻塞当前请求）
    if (cacheable(response.config)) {
      writeCache(cacheKey(response.config.url, response.config.params), res.data)
    }
    return res.data
  },
  async (error) => {
    const status = error.response?.status
    if (status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      router.replace({ name: 'login' })
      showToast('请先登录')
    } else if (backendUnreachable(error)) {
      // 连不上或超时：记下来，接下来的请求先别再排队等超时
      markBackendDown()
      reportUnreachable()
      const offline = markHandled(error)
      offline.offline = true
      // 502/504 是 nginx 转发时后端没起来的样子，提示按「后端没启动」来说更准确
      offline.backendDown = true
      // 断网时的读接口：本地有副本就直接用。这条路径不弹提示 ——
      // 能照常看到内容，再报一句「网络异常」只是添乱
      if (cacheable(error.config)) {
        const url = error.config.url
        const firstPage = Number(error.config.params?.page || 1) === 1
        const cached =
          (await readCache(cacheKey(url, error.config.params))) ??
          // 只有第一页才跨参数兜底：翻页时拿别页的数据顶上会出现重复内容
          (firstPage ? await readLatestCache(url) : null)
        if (cached != null) return cached
      }
      // 没有本地副本：要么是超时，要么是根本没连上，两种提示分开才好排查
      // silent：调用方会给出更贴切的说明（例如「已离线保存」），这里就不再插一句
      // 彻底断网时顶部已经有「离线中」的常驻提示，读接口再弹一次只是重复
      const quietOffline = cacheable(error.config) && navigator.onLine === false
      if (!error.config?.silent && !quietOffline) {
        toastNetworkIssue(error)
      }
      return Promise.reject(offline)
    } else {
      showToast(error.response.data?.message || '服务器开小差了，请稍后再试')
      reportReachable()
    }
    return Promise.reject(markHandled(error))
  }
)

/** 静默窗口内主动失败用的错误：形状与 axios 的网络错误保持一致，走同一套离线处理 */
function fastFail(config) {
  const error = new Error('backend-unreachable')
  error.code = 'ERR_BACKEND_DOWN'
  error.config = config
  return error
}

/**
 * 后端真的连不上，而不是「后端回了话但报错」。
 *
 * 经过 nginx 时后端没启动不会表现成网络错误，而是 502/503/504 网关错误，
 * 这种情况也要走离线兜底，否则缓存不再是「原地顶上」，页面只会看到一闪而过的报错。
 */
function backendUnreachable(error) {
  if (!error.response) return true
  const status = error.response.status
  return status === 502 || status === 503 || status === 504
}

// 后端没起来时，一屏会有好几个请求同时失败，同一句话连着弹五六次只是噪音
let lastNetworkToastAt = 0
const NETWORK_TOAST_GAP = 8000

function toastNetworkIssue(error) {
  const now = Date.now()
  if (now - lastNetworkToastAt < NETWORK_TOAST_GAP) return
  lastNetworkToastAt = now
  if (navigator.onLine === false) {
    showToast('当前没有网络，检查一下网络连接')
    return
  }
  if (error.code === 'ERR_BACKEND_DOWN' || error.backendDown) {
    showToast('连不上服务器，请确认后端已经启动')
    return
  }
  const isTimeout = error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')
  showToast(isTimeout ? '请求超时，请稍后再试' : '网络异常，请检查网络后重试')
}

function markHandled(error) {
  if (error) error.handled = true
  return error
}

export default request
