import { ref } from 'vue'
import { changelog, currentNotice, loadChangelog } from '../config/changelog'

const STORAGE_KEY = 'xinyu:notice:version'

// 模块级 ref 充当轻量全局状态，Profile 页和 App 根组件共用同一个弹窗
const visible = ref(false)

function readSeenVersion() {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function writeSeenVersion(version) {
  try {
    localStorage.setItem(STORAGE_KEY, version)
  } catch {
    // 隐私模式下 localStorage 不可写，忽略即可，下次进入会再提示一次
  }
}

export function useUpdateNotice() {
  /** 只有存在当前版本的更新说明、且用户还没读过这个版本时才弹 */
  function openIfUpdated() {
    return loadChangelog().then(({ version }) => {
      if (!version) return
      if (!currentNotice()) return
      if (readSeenVersion() === version) return
      visible.value = true
    })
  }

  /** 主动打开（「我的」页点「更新说明」时用）：当前版本没写说明也能翻历史 */
  function open() {
    visible.value = true
  }

  /**
   * 关闭即记为已读，之后直到下个版本才会再弹。
   * 当前版本还没有更新说明时不算「已读」—— 否则用户顺手翻一次历史，
   * 就把本该看到的发版说明给吃掉，再也不会自动弹了。
   */
  function close() {
    if (currentNotice() && changelog.version) {
      writeSeenVersion(changelog.version)
    }
    visible.value = false
  }

  return { visible, openIfUpdated, open, close }
}
