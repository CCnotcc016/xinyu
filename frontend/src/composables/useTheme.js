// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
import { computed, ref, watch } from 'vue'

/**
 * 显示模式：浅色 / 深色 / 跟随系统，由用户自己选，选择记在 localStorage 里。
 *
 * 真正的配色由 App.vue 里的 van-config-provider 负责，
 * 它会往 <html> 上加 van-theme-dark，Vant 的深色变量全都挂在这个类下面。
 */
const STORAGE_KEY = 'xinyu:theme'

/** 可选项，数组顺序就是设置页里的展示顺序 */
export const THEME_OPTIONS = [
  { value: 'auto', label: '跟随系统' },
  { value: 'light', label: '浅色模式' },
  { value: 'dark', label: '深色模式' }
]

const MODES = THEME_OPTIONS.map((item) => item.value)

const query = window.matchMedia('(prefers-color-scheme: dark)')
const systemDark = ref(query.matches)

function readMode() {
  const saved = localStorage.getItem(STORAGE_KEY)
  // 老版本没有这个 key，或者被手动改坏了，一律退回「跟随系统」
  return MODES.includes(saved) ? saved : 'auto'
}

const mode = ref(readMode())

// 只有「跟随系统」时才需要用上它，但监听本身很便宜，一直留着即可
query.addEventListener('change', (event) => {
  systemDark.value = event.matches
})

const theme = computed(() => (mode.value === 'auto' ? (systemDark.value ? 'dark' : 'light') : mode.value))

// 同时告诉浏览器当前配色，滚动条、原生输入框、下拉框等浏览器自带控件才会一起变暗
watch(
  theme,
  (value) => {
    document.documentElement.style.colorScheme = value
  },
  { immediate: true }
)

export function useTheme() {
  function setMode(next) {
    if (!MODES.includes(next)) {
      return
    }
    mode.value = next
    localStorage.setItem(STORAGE_KEY, next)
  }

  /** 当前偏好的展示名，设置页直接拿来当右侧文案 */
  const modeLabel = computed(
    () => THEME_OPTIONS.find((item) => item.value === mode.value)?.label || '跟随系统'
  )

  return { mode, modeLabel, theme, isDark: computed(() => theme.value === 'dark'), setMode }
}
