import { logout as revokeToken } from '../api/auth'
import { useUserStore } from '../stores/user'

/**
 * 主动登出。
 *
 * 先请服务端把 token_version +1，让这枚 token 立刻作废（泄漏出去的也一起失效），
 * 再清本机状态。网络不通 / token 已过期时也要照常下线——本地凭据不能因为
 * 发不出请求就一直留着。
 */
export async function logoutAndClear() {
  try {
    await revokeToken()
  } catch {
    // 离线或 token 已失效：忽略，继续本地清理
  }
  useUserStore().logout()
}
