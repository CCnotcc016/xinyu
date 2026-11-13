/**
 * 心语的离线存储层：IndexedDB 的最小封装。
 *
 * 两个库：
 *   · cache   —— 接口 GET 结果的副本（键是「用户 + 路径 + 参数」），断网时拿它顶上
 *   · outbox  —— 离线写的日记，等着联网补传
 *
 * 数据只留在本机。退出登录时 cache 会被清掉（见 stores/user.js）；outbox 保留 ——
 * 那是用户自己写的、还没同步上去的内容，删掉就真丢了。
 */

const DB_NAME = 'xinyu-offline'
const DB_VERSION = 1

export const STORE_CACHE = 'cache'
export const STORE_OUTBOX = 'outbox'

// 无痕模式 / 老 WebView 可能没有 IndexedDB，或者 open 被拒绝：
// 这时退化成内存 Map，本次会话内功能照常，只是关掉页面就没了。
const memory = { [STORE_CACHE]: new Map(), [STORE_OUTBOX]: new Map() }
let degraded = false
let dbPromise = null

function getDB() {
  if (dbPromise) return dbPromise
  dbPromise = new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('indexedDB unavailable'))
      return
    }
    const req = indexedDB.open(DB_NAME, DB_VERSION)
    req.onupgradeneeded = () => {
      const db = req.result
      // cache 没有 keyPath：写入时用「用户|方法|路径|参数」当键
      if (!db.objectStoreNames.contains(STORE_CACHE)) db.createObjectStore(STORE_CACHE)
      // outbox 每条自带 id（local_xxx），补传成功后按 id 摘掉
      if (!db.objectStoreNames.contains(STORE_OUTBOX)) {
        db.createObjectStore(STORE_OUTBOX, { keyPath: 'id' })
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
    req.onblocked = () => reject(new Error('indexedDB blocked'))
  }).catch(() => {
    degraded = true
    return null
  })
  return dbPromise
}

function resultOf(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

function committed(tx) {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}

export async function idbGet(store, key) {
  const db = await getDB()
  if (!db) return memory[store].get(key) ?? null
  const tx = db.transaction(store, 'readonly')
  return resultOf(tx.objectStore(store).get(key))
}

/** cache 这类「键由外面给」的库用这个 */
export async function idbSet(store, key, value) {
  const db = await getDB()
  if (!db) {
    memory[store].set(key, value)
    return
  }
  const tx = db.transaction(store, 'readwrite')
  tx.objectStore(store).put(value, key)
  await committed(tx)
}

/** outbox 这类 keyPath 在对象里的库用这个 */
export async function idbPut(store, value) {
  const db = await getDB()
  if (!db) {
    memory[store].set(value.id, value)
    return
  }
  const tx = db.transaction(store, 'readwrite')
  tx.objectStore(store).put(value)
  await committed(tx)
}

export async function idbDelete(store, key) {
  const db = await getDB()
  if (!db) {
    memory[store].delete(key)
    return
  }
  const tx = db.transaction(store, 'readwrite')
  tx.objectStore(store).delete(key)
  await committed(tx)
}

export async function idbGetAll(store) {
  const db = await getDB()
  if (!db) return Array.from(memory[store].values())
  const tx = db.transaction(store, 'readonly')
  return resultOf(tx.objectStore(store).getAll()).then((rows) => rows || [])
}

export async function idbKeys(store) {
  const db = await getDB()
  if (!db) return Array.from(memory[store].keys())
  const tx = db.transaction(store, 'readonly')
  return resultOf(tx.objectStore(store).getAllKeys()).then((keys) => keys || [])
}

export async function idbClear(store) {
  const db = await getDB()
  if (!db) {
    memory[store].clear()
    return
  }
  const tx = db.transaction(store, 'readwrite')
  tx.objectStore(store).clear()
  await committed(tx)
}
