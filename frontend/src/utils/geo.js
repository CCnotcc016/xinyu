/**
 * 定位的统一入口。
 *
 * 为什么不直接用 navigator.geolocation：
 *   浏览器里那套是「网页定位」，手机浏览器只在 https / localhost 下开放；
 *   而装成 App 之后，WebView 里的 navigator.geolocation 要经过
 *   WebChromeClient.onGeolocationPermissionsShowPrompt，没有原生权限就永远是 PERMISSION_DENIED，
 *   弹出来的也不是系统那个「心语 想要使用你的位置信息」的授权框。
 *
 * 所以 App 里走 @capacitor/geolocation：它会先申请 Android 运行时权限
 * （ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION），用户看到的是正常的系统弹窗，
 * 之后可以在「设置 → 应用 → 心语 → 权限 → 位置信息」里随时改。
 * 浏览器里仍然退回 navigator.geolocation，网页体验不变。
 */

import { Capacitor } from '@capacitor/core'
import { Geolocation } from '@capacitor/geolocation'

const DEFAULT_TIMEOUT = 12000

/** 是不是装在手机上的 App（而不是浏览器里打开的网页） */
export function isNativeApp() {
  try {
    return Capacitor.isNativePlatform()
  } catch {
    return false
  }
}

/** 这个环境有没有定位能力 */
export function geoSupported() {
  return isNativeApp() || !!navigator.geolocation
}

/**
 * 取一次坐标。返回 { pos } 或 { reason }，一律不抛异常 ——
 * reason 和浏览器的 error.code 语义对齐：denied / timeout / unavailable。
 */
export async function getCurrentPosition(options = {}) {
  if (isNativeApp()) return nativePosition(options)
  return browserPosition(options)
}

/** 蹲一次位置：定位芯片冷启动时，持续监听往往比单次请求更容易拿到结果 */
export async function watchOnce(options = {}) {
  const timeout = options.timeout || DEFAULT_TIMEOUT
  if (isNativeApp()) {
    return new Promise((resolve) => {
      let settled = false
      let watchId = null
      const finish = (value) => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        if (watchId) Geolocation.clearWatch({ id: watchId }).catch(() => {})
        resolve(value)
      }
      const timer = setTimeout(() => finish(null), timeout)
      Geolocation.watchPosition(
        { enableHighAccuracy: !!options.enableHighAccuracy, timeout, maximumAge: 10 * 60 * 1000 },
        (pos) => {
          if (pos) finish(pos)
        }
      )
        .then((id) => {
          watchId = id
          if (settled) Geolocation.clearWatch({ id }).catch(() => {})
        })
        .catch(() => finish(null))
    })
  }

  if (!navigator.geolocation) return null
  return new Promise((resolve) => {
    let settled = false
    let watchId = 0
    const finish = (value) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      if (watchId) {
        try {
          navigator.geolocation.clearWatch(watchId)
        } catch {
          // 个别浏览器清理失败不影响结果
        }
      }
      resolve(value)
    }
    const timer = setTimeout(() => finish(null), timeout)
    watchId = navigator.geolocation.watchPosition(
      (pos) => finish(pos),
      () => finish(null),
      { enableHighAccuracy: false, timeout, maximumAge: 10 * 60 * 1000 }
    )
  })
}

/**
 * 已经明确拒绝过的话就没必要再试，直接引导用户去开。
 * 'unknown' 表示查不出来（部分浏览器没有 permissions.query），按没拒绝处理。
 */
export async function permissionState() {
  if (isNativeApp()) {
    try {
      const perm = await Geolocation.checkPermissions()
      if (perm.location === 'denied') return 'denied'
      return perm.location === 'granted' ? 'granted' : 'prompt'
    } catch {
      return 'unknown'
    }
  }
  try {
    const perm = await navigator.permissions?.query({ name: 'geolocation' })
    return perm?.state || 'unknown'
  } catch {
    return 'unknown'
  }
}

function browserPosition(options) {
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ pos }),
      (err) => resolve({ reason: reasonOf(err) }),
      options
    )
  })
}

/**
 * 原生侧的两个坑，这里一次处理掉：
 *  1. 权限还在 prompt 状态时不会自己弹框，必须显式 requestPermissions()；
 *  2. 用户选了「仅本次允许」，之后每次都要重新确认，转发请求即可。
 */
async function nativePosition(options) {
  try {
    const perm = await Geolocation.checkPermissions()
    if (perm.location === 'denied') return { reason: 'denied' }
    if (perm.location !== 'granted') {
      const asked = await Geolocation.requestPermissions({ permissions: ['location'] })
      if (asked.location === 'denied') return { reason: 'denied' }
    }
    const pos = await Geolocation.getCurrentPosition({
      enableHighAccuracy: !!options.enableHighAccuracy,
      timeout: options.timeout || DEFAULT_TIMEOUT,
      maximumAge: options.maximumAge || 0
    })
    return { pos }
  } catch (error) {
    return { reason: nativeReason(error) }
  }
}

function reasonOf(err) {
  if (!err) return 'unavailable'
  if (err.code === 1) return 'denied'
  if (err.code === 3) return 'timeout'
  return 'unavailable'
}

function nativeReason(error) {
  const msg = String(error?.message || '')
  if (/denied|permission/i.test(msg)) return 'denied'
  if (/timeout|timed out/i.test(msg)) return 'timeout'
  return 'unavailable'
}
