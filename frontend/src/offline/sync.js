import axios from 'axios'
import { registerClient } from '../utils/backend'
import { deleteOutboxItem, listOutbox, putOutboxItem } from './outbox'

/**
 * 补传：把待同步队列里的日记按时间顺序提交给服务器。
 *
 * 这里不走 utils/request.js —— 那层失败会弹 Toast，而后台自动重试是静默进行的，
 * 断网时每 20 秒弹一次「网络异常」会把人烦死。所以单独一个安静的 axios 实例，
 * 失败只用来判断「通不通」，不打扰用户。
 */
const quiet = registerClient(axios.create({ timeout: 10000 }))

function authHeader() {
  const token = localStorage.getItem('xinyu_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function isNetworkError(error) {
  // 没有 response：DNS 失败、连接被拒、断网、超时
  return !error?.response
}

/**
 * 补传全部待同步日记。
 * 返回 { synced, remaining, failed, offline }：
 *   · offline 为 true 表示「连不上」，界面据此显示离线；
 *   · failed 是服务器明确拒绝的条数（内容有问题之类），这些不会再自动重试。
 */
export async function flushOutbox(userId) {
  const items = await listOutbox(userId)
  if (!items.length) return { synced: 0, remaining: 0, failed: 0, offline: false, mapping: {} }

  let synced = 0
  let failed = 0
  // 本地 id → 服务器 id：详情页正开着这篇时，同步完可以直接跳过去
  const mapping = {}

  for (const item of items) {
    try {
      const { data } = await quiet.post('/diaries', item.payload, { headers: authHeader() })
      if (data?.code !== 0) {
        // 服务端明确拒绝（校验不通过等）：记下原因，等下一个人工重试，别无限刷
        await putOutboxItem({ ...item, attempts: (item.attempts || 0) + 1, lastError: data?.message || '同步失败' })
        failed += 1
        continue
      }
      const realId = data?.data?.id
      if (realId != null) mapping[item.id] = realId
      await deleteOutboxItem(item.id)
      synced += 1
    } catch (error) {
      if (isNetworkError(error)) {
        // 连不上就整体停下，剩下的留到下次；已同步的已经出队了
        const remaining = (await listOutbox(userId)).length
        return { synced, remaining, failed, offline: true, mapping }
      }
      const status = error?.response?.status
      const message = error?.response?.data?.message || (status ? `服务器错误 ${status}` : '同步失败')
      // 401：登录过期，整批留着等重新登录后再传
      if (status === 401) {
        const remaining = (await listOutbox(userId)).length
        return { synced, remaining, failed, offline: true, mapping }
      }
      await putOutboxItem({ ...item, attempts: (item.attempts || 0) + 1, lastError: message })
      failed += 1
    }
  }

  const remaining = (await listOutbox(userId)).length
  return { synced, remaining, failed, offline: false, mapping }
}
