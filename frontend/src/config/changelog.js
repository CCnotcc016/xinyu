import { computed, reactive } from 'vue'

/**
 * 更新说明改为运行时从 public/changelog.json 读取。
 *
 * 以前版本号和更新说明写死在这个 .js 里，改一行字都要重新 npm run build、
 * 重新打包镜像；现在只要改 changelog.json 再刷新页面就能看到。发布新版本时
 * 记得同时改 changelog.json 里的 version 和条目，弹窗靠它对版本号。
 */
const SOURCE = `${import.meta.env.BASE_URL}changelog.json`
const CACHE_KEY = 'xinyu:changelog:cache'

/** 全局共享状态，组件直接读它，加载完成后自动响应式刷新 */
export const changelog = reactive({
  version: '',
  entries: [],
  loaded: false,
})

/** 「我的」页、设置页展示的版本号，读不到时给个占位符，别显示 vundefined */
export const versionLabel = computed(() => (changelog.version ? `v${changelog.version}` : '—'))

let pending = null

function isEntry(entry) {
  return !!entry && typeof entry === 'object' && typeof entry.version === 'string' && Array.isArray(entry.items)
}

/** 校验并裁剪成内部结构，格式不对时返回 null，让调用方走兜底 */
function parse(raw) {
  if (!raw || typeof raw !== 'object') return null
  const version = typeof raw.version === 'string' ? raw.version.trim() : ''
  const entries = Array.isArray(raw.changelog) ? raw.changelog.filter(isEntry) : []
  if (!version || !entries.length) return null
  return { version, entries }
}

function apply(data) {
  changelog.version = data.version
  changelog.entries.splice(0, changelog.entries.length, ...data.entries)
  changelog.loaded = true
}

function readCache() {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    return raw ? parse(JSON.parse(raw)) : null
  } catch {
    return null
  }
}

function writeCache(data) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify({ version: data.version, changelog: data.entries }))
  } catch {
    // 隐私模式下不可写，忽略；断网时就只能退化成没有更新说明
  }
}

async function fetchChangelog() {
  try {
    const res = await fetch(SOURCE, { cache: 'no-store' })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data = parse(await res.json())
    if (!data) throw new Error('changelog.json 格式不正确')
    apply(data)
    writeCache(data)
    return data
  } catch (err) {
    // 断网或文件缺失时用上次成功读取的副本，至少历史说明还能翻
    const cached = readCache()
    if (cached) {
      apply(cached)
      console.warn('[changelog] 读取 changelog.json 失败，改用本地缓存：', err.message)
      return cached
    }
    changelog.loaded = true
    pending = null // 允许下次进入或刷新时重试
    console.warn('[changelog] 无法读取 changelog.json：', err.message)
    return { version: '', entries: [] }
  }
}

/** 全局只发一次请求，重复调用复用同一个 promise */
export function loadChangelog() {
  if (!pending) pending = fetchChangelog()
  return pending
}

/** 当前版本对应的那条更新说明；没写就返回 null（此时不弹窗，也不记为已读） */
export function currentNotice() {
  return changelog.entries.find((entry) => entry.version === changelog.version) || null
}

// 一进页面就开始取，弹窗判定、版本号展示都不用各自等
loadChangelog()
