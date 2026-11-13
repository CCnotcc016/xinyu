/**
 * 「今日情绪天气」：拿用户所在地的实时天气，配一句和情绪有关的话。
 *
 * 三条设计原则，改动前请先读一下：
 *  1. 必须先征得同意。定位授权存在后端 user.location_consent 上，这里只读不写，
 *     开关动作统一走 setConsent()，保证「同意 / 撤回」都有服务端时间戳。
 *  2. 坐标不上传心语后端。它只存在浏览器 localStorage，用于直接向 Open-Meteo 查询；
 *     撤回授权时随缓存一起删掉。隐私政策里对用户就是这么承诺的。
 *  3. 拿不到定位不算失败。可以退回手动选城市，功能完全一样。
 */

import { computed, reactive } from 'vue'
import { showNotify } from 'vant'
import { updateLocationConsent } from '../api/user'
import { useUserStore } from '../stores/user'
import {
  geoSupported,
  getCurrentPosition,
  isNativeApp,
  permissionState,
  watchOnce as watchPositionOnce
} from '../utils/geo'

const COORDS_KEY = 'xinyu:weather:coords'
const SNAPSHOT_KEY = 'xinyu:weather:snapshot'
const NOTIFY_KEY = 'xinyu:weather:notified'

/** 天气半小时内不重复请求，避免来回切页把免费接口打爆 */
const SNAPSHOT_TTL = 30 * 60 * 1000
const GEO_TIMEOUT = 12000

/**
 * 定位失败的原因 → 告诉用户该怎么办。
 * 「拿不到定位」这句话本身没信息量，用户需要知道的是下一步做什么。
 */
export const GEO_HINTS = {
  unsupported: {
    title: '这个浏览器没有开放定位',
    tip: '可以手动选择城市，天气功能完全一样。'
  },
  insecure: {
    title: '当前不是 HTTPS，手机浏览器不允许定位',
    tip: '华为、小米等自带浏览器只在 https 页面开放定位接口。可以手动选择城市，'
      + '或者把心语换成 https 地址再打开。'
  },
  denied: {
    title: '定位权限被拒绝了',
    tip: '点地址栏左侧的站点图标，把「位置信息」改成允许，并确认手机的定位总开关是打开的，'
      + '然后回来重试。'
  },
  unavailable: {
    title: '暂时定不到位',
    tip: '可能是信号弱或开了省电模式。换个地方再试，也可以直接手动选择城市。'
  },
  timeout: {
    title: '定位超时了',
    tip: '到窗边或室外再试一次，或者直接手动选择城市。'
  }
}

/** App 里没有地址栏，权限开关在系统设置里，提示得换个说法 */
const GEO_HINTS_NATIVE = {
  insecure: {
    title: '当前环境不允许定位',
    tip: '可以手动选择城市，天气功能完全一样。'
  },
  denied: {
    title: '定位权限被拒绝了',
    tip: '打开「设置 → 应用 → 心语 → 权限 → 位置信息」，选择「使用时允许」，'
      + '并确认手机的定位总开关是打开的，然后回来重试。也可以直接手动选择城市。'
  }
}

/** 按当前环境挑一版提示：App 里说系统设置，网页里说地址栏 */
export function geoHintFor(reason) {
  const key = reason && GEO_HINTS[reason] ? reason : 'unavailable'
  const native = isNativeApp() ? GEO_HINTS_NATIVE[key] : null
  return native || GEO_HINTS[key]
}

/**
 * 手机时区 → 城市名。定位失败时拿它给手选城市的搜索框预填一个词，
 * 纯粹为了省一次打字：猜错也没关系，用户随时能改，同样不联网、不上传。
 */
const TIMEZONE_CITY = {
  'Asia/Shanghai': '上海',
  'Asia/Chongqing': '重庆',
  'Asia/Urumqi': '乌鲁木齐',
  'Asia/Harbin': '哈尔滨',
  'Asia/Kashgar': '喀什',
  'Asia/Hong_Kong': '香港',
  'Asia/Macau': '澳门',
  'Asia/Taipei': '台北'
}

const FORECAST_API = 'https://api.open-meteo.com/v1/forecast'
const GEOCODING_API = 'https://geocoding-api.open-meteo.com/v1/search'
const REVERSE_API = 'https://api.bigdatacloud.net/data/reverse-geocode-client'

// 模块级状态：首页天气卡片和写日记页的「自动填入天气」共用同一份数据
const state = reactive({
  status: 'idle', // idle | loading | ready | error
  coords: readJson(COORDS_KEY),
  snapshot: readJson(SNAPSHOT_KEY),
  error: ''
})

function readJson(key) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function writeJson(key, value) {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // 隐私模式 / 存储写满时静默降级：功能还在，只是不缓存
  }
}

/** 缓存里那份还得及用吗 */
function isFresh(snapshot) {
  return !!snapshot && Date.now() - (snapshot.fetchedAt || 0) < SNAPSHOT_TTL
}

async function getJson(url) {
  const resp = await fetch(url)
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
  return resp.json()
}

/**
 * WMO 天气代码 → 图标 + 文案 + 一句和情绪有关的话。
 * 分组而不是逐码映射：用户不需要知道「冻雨」和「毛毛雨」的区别，
 * 但「下雨」和「下雪」的陪伴感是两回事。
 */
const WEATHER_CODES = [
  { codes: [0], icon: '☀️', text: '晴', day: '今天阳光不错，记得抬头看看天。', night: '晴天的夜里，星星会比平时亮一点。' },
  { codes: [1, 2], icon: '🌤️', text: '多云', day: '云在走，你也可以慢慢来。', night: '云散了一些，月亮露出来了。' },
  { codes: [3], icon: '☁️', text: '阴', day: '阴天不一定要有情绪，安静也挺好。', night: '阴天的晚上，屋里会更暖和一点。' },
  { codes: [45, 48], icon: '🌫️', text: '有雾', day: '看不清远处也没关系，先走好脚下这一步。', night: '雾里看不清路，慢一点更安全。' },
  { codes: [51, 53, 55, 56, 57], icon: '🌦️', text: '毛毛雨', day: '雨很小，但记得带伞。', night: '外面下着小雨，正好适合早点休息。' },
  { codes: [61, 63, 65, 66, 67, 80, 81, 82], icon: '🌧️', text: '下雨', day: '下雨了，雨声其实很适合写点什么。', night: '听着雨声，把今天放下吧。' },
  { codes: [71, 73, 75, 77, 85, 86], icon: '🌨️', text: '下雪', day: '下雪了，路滑，走慢一点。', night: '下雪的夜里，早点回暖和的地方。' },
  { codes: [95, 96, 99], icon: '⛈️', text: '雷阵雨', day: '打雷了，别站在空旷的地方。', night: '雷声有点大，把耳机戴上会好一些。' }
]

export function weatherInfo(code, isDay = 1) {
  const hit = WEATHER_CODES.find((item) => item.codes.includes(code)) || {
    icon: '🌡️',
    text: '天气',
    day: '不管外面什么天气，先照顾好自己。',
    night: '不管外面什么天气，先照顾好自己。'
  }
  return { icon: hit.icon, text: hit.text, tip: isDay ? hit.day : hit.night }
}

/** 折成日记里那四个天气选项，供写日记页自动填入 */
export function weatherOption(code) {
  const icon = weatherInfo(code).icon
  if (icon === '☀️' || icon === '🌤️') return '晴天'
  if (icon === '🌨️') return '雪天'
  if (icon === '🌧️' || icon === '🌦️' || icon === '⛈️') return '雨天'
  return '阴天'
}

/**
 * 浏览器只在安全上下文里给定位权限。http://localhost 也算安全上下文，
 * 但 http://192.168.x.x（手机访问电脑的常规方式）不算——华为、小米等自带浏览器都会直接拒绝。
 * App 里页面来自 https://localhost，本身就是安全上下文，这条判断只对网页生效。
 */
function isSecurePage() {
  if (isNativeApp()) return true
  if (window.isSecureContext) return true
  const host = location.hostname
  return host === 'localhost' || host === '127.0.0.1' || host === '::1' || host === '[::1]'
}

export function useWeather() {
  const userStore = useUserStore()

  /** 授权状态以后端记录为准；未登录时一律当作未授权 */
  const consent = computed(() => userStore.user?.locationConsent === true)
  const status = computed(() => state.status)
  const error = computed(() => state.error)
  const weather = computed(() => state.snapshot)
  const place = computed(() => state.coords?.city || '')
  const hasCoords = computed(() => !!state.coords)
  /** 页面上是不是 https / localhost：不是的话，浏览器根本不会给定位 */
  const securePage = computed(() => isSecurePage())

  /** 拉一次最新天气。已有半小时内的缓存就直接用 */
  async function load({ force = false } = {}) {
    if (!state.coords) {
      state.status = 'idle'
      return
    }
    if (!force && isFresh(state.snapshot)) {
      state.status = 'ready'
      return
    }
    state.status = 'loading'
    state.error = ''
    try {
      const { lat, lon } = state.coords
      const data = await getJson(
        `${FORECAST_API}?latitude=${lat}&longitude=${lon}` +
          '&current=temperature_2m,apparent_temperature,weather_code,is_day' +
          '&daily=weather_code,temperature_2m_max,temperature_2m_min' +
          '&timezone=auto&forecast_days=1'
      )
      state.snapshot = {
        code: data.current?.weather_code ?? 0,
        isDay: data.current?.is_day ?? 1,
        temp: Math.round(data.current?.temperature_2m ?? 0),
        apparent: Math.round(data.current?.apparent_temperature ?? 0),
        max: Math.round(data.daily?.temperature_2m_max?.[0] ?? 0),
        min: Math.round(data.daily?.temperature_2m_min?.[0] ?? 0),
        fetchedAt: Date.now()
      }
      writeJson(SNAPSHOT_KEY, state.snapshot)
      state.status = 'ready'
    } catch {
      state.status = 'error'
      state.error = '天气暂时拿不到，可能是网络问题'
    }
  }

  /** 保存一份坐标（定位或手选城市都走这里），随后立即取天气 */
  async function setCoords(lat, lon, city, manual) {
    state.coords = { lat, lon, city: city || '', manual: !!manual }
    writeJson(COORDS_KEY, state.coords)
    await load({ force: true })
  }

  /**
   * 请求系统定位，返回 { ok, reason }。拿不到定位不算失败，调用方引导用户手选城市即可。
   *
   * 走 utils/geo.js：App 里是原生权限（系统弹窗 + 可在系统设置里收回），
   * 网页里还是浏览器定位。手机浏览器只在「安全上下文」开放定位 —— https 页面或
   * http://localhost 可以，http://192.168.x.x 会被直接拒绝，所以先把这句话说清楚。
   */
  async function requestLocation() {
    if (!geoSupported()) return { ok: false, reason: 'unsupported' }
    if (!isSecurePage()) return { ok: false, reason: 'insecure' }

    // 已被永久拒绝的话再试也没用，直接告诉用户去哪儿开（App 里是系统设置）
    if ((await permissionState()) === 'denied') return { ok: false, reason: 'denied' }

    // 国产浏览器与安卓机型对 enableHighAccuracy 的容忍度差别很大：有的只认基站定位。
    // 先拿一次粗略的，不行再要精确的。
    const attempts = [
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 10 * 60 * 1000 },
      { enableHighAccuracy: true, timeout: GEO_TIMEOUT, maximumAge: 0 }
    ]
    let reason = 'timeout'
    for (const options of attempts) {
      const { pos, reason: failed } = await getCurrentPosition(options)
      if (pos) return acceptPosition(pos)
      reason = failed || 'unavailable'
      if (reason === 'denied') return { ok: false, reason }
    }

    // 最后蹲一次：定位芯片冷启动时，持续监听往往比单次请求更容易拿到结果
    const watched = await watchPositionOnce()
    if (watched) return acceptPosition(watched)
    return { ok: false, reason }
  }

  /** 手选城市搜索框的预填词 */
  function guessCity() {
    try {
      const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || ''
      return TIMEZONE_CITY[tz] || ''
    } catch {
      return ''
    }
  }

  async function acceptPosition(pos) {
    const { latitude, longitude } = pos.coords
    state.coords = { lat: latitude, lon: longitude, city: '', manual: false }
    // 顺手反查城市名，只为把「当前位置」显示成「北京市」；失败不影响天气本身
    const city = await reverseCity(latitude, longitude)
    await setCoords(latitude, longitude, city, false)
    return { ok: true }
  }

  async function reverseCity(lat, lon) {
    try {
      const data = await getJson(
        `${REVERSE_API}?latitude=${lat}&longitude=${lon}&localityLanguage=zh`
      )
      return data.city || data.locality || ''
    } catch {
      return ''
    }
  }

  /** 手动搜城市，结果给用户挑；这条路径完全不碰定位权限 */
  async function searchCity(keyword) {
    const name = (keyword || '').trim()
    if (!name) return []
    try {
      const data = await getJson(
        `${GEOCODING_API}?name=${encodeURIComponent(name)}&count=8&language=zh&format=json`
      )
      return (data.results || []).map((item) => ({
        label: [item.name, item.admin1, item.country].filter(Boolean).join(' · '),
        lat: item.latitude,
        lon: item.longitude
      }))
    } catch {
      return []
    }
  }

  /** 打开 / 关闭「定位与天气」，开关状态写回后端，撤回时顺手清掉本地坐标与天气 */
  async function setConsent(next) {
    userStore.setUser(await updateLocationConsent(next))
    if (next) {
      await load()
    } else {
      clear()
    }
  }

  function clear() {
    state.coords = null
    state.snapshot = null
    state.error = ''
    state.status = 'idle'
    writeJson(COORDS_KEY, null)
    writeJson(SNAPSHOT_KEY, null)
  }

  /**
   * 每天第一次打开时，用一条轻提示把天气「推」给用户。
   * 已经授权、且今天还没推过才会发，避免变成骚扰。
   */
  function notifyDaily() {
    if (!consent.value || state.status !== 'ready' || !state.snapshot) return
    const today = new Date().toLocaleDateString('sv')
    if (readJson(NOTIFY_KEY) === today) return
    writeJson(NOTIFY_KEY, today)
    const info = weatherInfo(state.snapshot.code, state.snapshot.isDay)
    const where = place.value ? `${place.value} ` : ''
    showNotify({
      message: `${where}${info.text} ${state.snapshot.temp}°C　${info.tip}`,
      type: 'primary',
      duration: 4000
    })
  }

  return {
    consent,
    status,
    error,
    weather,
    place,
    hasCoords,
    securePage,
    load,
    setCoords,
    requestLocation,
    guessCity,
    searchCity,
    setConsent,
    clear,
    notifyDaily
  }
}
