// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
import { ref } from 'vue'
import { APP_VERSION, currentNotice } from '../config/changelog'

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
    if (!currentNotice()) return
    if (readSeenVersion() === APP_VERSION) return
    visible.value = true
  }

  /** 主动打开（「我的」页点「更新说明」时用） */
  function open() {
    if (currentNotice()) visible.value = true
  }

  /** 关闭即记为已读，之后直到下个版本才会再弹 */
  function close() {
    writeSeenVersion(APP_VERSION)
    visible.value = false
  }

  return { visible, openIfUpdated, open, close }
}
