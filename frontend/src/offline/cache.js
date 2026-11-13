import { STORE_CACHE, idbClear, idbDelete, idbGet, idbKeys, idbSet } from './idb'

/**
 * 接口结果的本地副本。
 *
 * 只缓存 GET，键里带用户 id：同一台设备换账号登录时不会读到别人的日记。
 * 断网时 request.js 用它在原地兜底，页面代码完全不用改。
 */
const MAX_ENTRIES = 400

function currentUserId() {
  try {
    return JSON.parse(localStorage.getItem('xinyu_user') || 'null')?.id ?? 'anon'
  } catch {
    return 'anon'
  }
}

/** 参数顺序会影响拼接结果，先排序，避免同一请求存成两份 */
function stableParams(params) {
  if (!params) return ''
  return Object.keys(params)
    .sort()
    .map((k) => `${k}=${params[k] ?? ''}`)
    .join('&')
}

export function cacheKey(url, params) {
  return `u${currentUserId()}|${url}|${stableParams(params)}`
}

export async function readCache(key) {
  try {
    return (await idbGet(STORE_CACHE, key))?.data ?? null
  } catch {
    return null
  }
}

/**
 * 同接口不同参数时的兜底：找这份数据最近的另一份副本。
 *
 * 首页要 size=5 的最近日记，日记页要 page=1&size=10，键不一样；
 * 断网时用首页那份把日记列表填上，比空白页有用得多。
 */
export async function readLatestCache(url) {
  try {
    const prefix = `u${currentUserId()}|${url}|`
    const keys = (await idbKeys(STORE_CACHE)).filter((k) => k.startsWith(prefix))
    if (!keys.length) return null
    const rows = await Promise.all(keys.map(async (k) => await idbGet(STORE_CACHE, k)))
    const newest = rows.filter(Boolean).sort((a, b) => (b.at || 0) - (a.at || 0))[0]
    return newest?.data ?? null
  } catch {
    return null
  }
}

export async function writeCache(key, data) {
  try {
    await idbSet(STORE_CACHE, key, { data, at: Date.now() })
    await trim()
  } catch {
    // 写不进去（配额满 / 无痕模式）就算了，不能因为缓存失败影响这次正常请求
  }
}

/** IndexedDB 没有 TTL，超过上限就按写入时间清掉最旧的一部分，避免缓存无限长大 */
async function trim() {
  const keys = await idbKeys(STORE_CACHE)
  if (keys.length <= MAX_ENTRIES) return
  const rows = await Promise.all(keys.map(async (k) => ({ k, at: (await idbGet(STORE_CACHE, k))?.at || 0 })))
  rows.sort((a, b) => a.at - b.at)
  const stale = rows.slice(0, keys.length - MAX_ENTRIES)
  await Promise.all(stale.map((r) => idbDelete(STORE_CACHE, r.k)))
}

/** 退出登录 / 手动「清空离线缓存」时调用 */
export async function clearApiCache() {
  try {
    await idbClear(STORE_CACHE)
  } catch {
    // 清不掉也不该阻断退出登录
  }
}
