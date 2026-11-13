import { STORE_OUTBOX, idbClear, idbDelete, idbGet, idbGetAll, idbPut } from './idb'

/**
 * 待同步队列：离线时写下的日记先放这里，联网后按时间顺序补传。
 *
 * 每条记录既是「队列项」也是「本地日记」：diary 字段的形状和后端 DiaryVO 对齐，
 * 列表页、详情页拿到它就能直接渲染，不用区分数据来自服务器还是本地。
 *
 * id 用 local_ 前缀 —— 服务端 id 都是数字，一眼能分辨，
 * 详情页看到这种 id 就知道该去队列里取、AI 回复按钮也该禁用。
 */
export const LOCAL_PREFIX = 'local_'
export const MAX_ATTEMPTS = 5

export function isLocalId(id) {
  return String(id ?? '').startsWith(LOCAL_PREFIX)
}

export function newLocalId() {
  return LOCAL_PREFIX + Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

/** 幂等键：服务端按 (user_id, client_uid) 判重，超时重传也只会落一条 */
export function newClientUid() {
  const rand = Math.random().toString(36).slice(2, 10)
  return `w${Date.now().toString(36)}${rand}`
}

/** 本地时间格式，和后端 LocalDateTime 的序列化形式一致：2026-10-27T13:40:05 */
export function localTimestamp(date = new Date()) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export async function listOutbox(userId) {
  const rows = await idbGetAll(STORE_OUTBOX)
  return rows
    .filter((row) => row.userId === userId)
    .sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt)))
}

export async function countOutbox(userId) {
  return (await listOutbox(userId)).length
}

export async function getOutboxItem(id) {
  return idbGet(STORE_OUTBOX, id)
}

export async function putOutboxItem(item) {
  await idbPut(STORE_OUTBOX, item)
  return item
}

export async function deleteOutboxItem(id) {
  await idbDelete(STORE_OUTBOX, id)
}

/** 注销账号 / 清空本地数据时用 */
export async function clearOutbox() {
  await idbClear(STORE_OUTBOX)
}
