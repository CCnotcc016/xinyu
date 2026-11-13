import { createDiary, deleteDiary, getDiary, listDiaries } from '../api/diary'
import { containsCrisis, CRISIS_REPLY } from './crisis'
import {
  deleteOutboxItem,
  getOutboxItem,
  isLocalId,
  listOutbox,
  localTimestamp,
  newClientUid,
  newLocalId,
  putOutboxItem
} from './outbox'

/**
 * 日记的「离线优先」门面。
 *
 * 写：先照常提交给服务器；连不上（不是业务报错）就转成一条本地日记塞进待同步队列，
 *     页面拿到的对象形状和服务器返回的一致，用户感觉不到差别，只是多一个「待同步」标记。
 * 读：列表把待同步的本地日记并进去；详情按 id 判断去队列取还是走接口。
 */

/** 本地日记对象：字段和 DiaryVO 对齐，多一个 pending 供界面显示「待同步」 */
function toLocalDiary(id, userId, payload) {
  const crisis = containsCrisis(payload.content)
  return {
    id,
    userId,
    content: payload.content,
    emotionScore: null,
    emotionLabel: null,
    weather: payload.weather || null,
    scene: payload.scene || null,
    privacy: payload.privacy || 'PRIVATE',
    aiReply: crisis ? CRISIS_REPLY : null,
    aiStatus: crisis ? 'CRISIS' : 'PENDING',
    emotionTags: payload.emotionTags || [],
    createdAt: localTimestamp(),
    crisisSupport: crisis,
    pending: true
  }
}

async function saveLocalDiary(payload, userId) {  const id = newLocalId()
  const item = {
    id,
    userId,
    clientUid: newClientUid(),
    createdAt: localTimestamp(),
    payload: null,
    diary: toLocalDiary(id, userId, payload),
    attempts: 0,
    lastError: null
  }
  // payload 里带上 clientUid，补传时原样发回去
  item.payload = { ...payload, clientUid: item.clientUid }
  return putOutboxItem(item)
}

/**
 * 保存日记：能连上网就正常提交，连不上就本地存下来等补传。
 * 业务报错（内容为空之类）照旧抛给页面，不吞。
 */
export async function createDiarySmart(payload, userId) {
  // 表单里传下来的是 Vue 的响应式对象（emotionTags 是 Proxy），
  // IndexedDB 的结构化克隆搬不动 Proxy，会抛 DataCloneError。
  // 这里先折成纯数据，后面无论存本地还是发请求都用它。
  const body = JSON.parse(JSON.stringify(payload ?? {}))
  try {
    // clientUid 先算好：即使这次请求超时但其实已落库，补传时也能被服务端认出来
    const diary = await createDiary({ ...body, clientUid: newClientUid() })
    return { diary, offline: false }
  } catch (error) {
    if (!error?.offline) throw error
    const item = await saveLocalDiary(body, userId)
    return { diary: item.diary, offline: true }
  }
}

function byCreatedAtDesc(a, b) {
  return String(b.createdAt).localeCompare(String(a.createdAt))
}

/**
 * 日记列表：接口结果 + 待同步的本地日记。
 * 第一页才合并 —— 本地日记永远排在最前面，翻第二页时再塞进去会重复。
 */
export async function listDiariesMerged(params, userId) {
  const page = params?.page ?? 1
  const local = page === 1 ? (await listOutbox(userId)).map((item) => item.diary) : []
  try {
    const data = await listDiaries(params)
    if (!local.length) return data
    return {
      ...data,
      records: [...local, ...(data.records || [])].sort(byCreatedAtDesc),
      total: (data.total || 0) + local.length
    }
  } catch (error) {
    if (!error?.offline || page !== 1) throw error
    // 接口连不上、也没有缓存：至少把本地待同步的日记显示出来
    return { records: local, total: local.length, page: 1, size: params?.size ?? 10, offline: true }
  }
}

export async function getDiaryAny(id) {
  if (!isLocalId(id)) return getDiary(id)
  const item = await getOutboxItem(id)
  return item?.diary ?? null
}

/**
 * 删除：本地日记直接从队列摘掉；已同步的日记要连服务器。
 * 离线时不排队删除 —— 删一半同步一半的状态最难解释，不如直接告诉用户等联网。
 */
export async function deleteDiaryAny(id) {
  if (isLocalId(id)) {
    await deleteOutboxItem(id)
    return
  }
  await deleteDiary(id)
}
