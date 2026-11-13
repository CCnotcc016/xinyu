import { watch } from 'vue'
import { Capacitor } from '@capacitor/core'
import { App as CapacitorApp } from '@capacitor/app'
import { StatusBar, Style } from '@capacitor/status-bar'
import { SplashScreen } from '@capacitor/splash-screen'
import router from './router'
import { useTheme } from './composables/useTheme'

/**
 * 原生外壳：只在打好的 App 里生效，浏览器打开时整段跳过。
 *
 * 这些能力 WebView 默认都没有 —— 状态栏会是一条白条、返回键直接退出 App、
 * 启动图一直挂着不消失。放在这里统一处理，页面代码不用关心自己跑在哪儿。
 */
const TAB_ROUTES = new Set(['home', 'diaries', 'treehole', 'profile'])

/** 和 assets/main.css 里的 --xinyu-page-base 保持一致，状态栏才不会和页面差一截颜色 */
const BAR_COLOR = { dark: '#06101a', light: '#eaf2f5' }

function applyStatusBar(dark) {
  // 这三个方法只有 Android 支持，iOS 上会抛，忽略即可
  StatusBar.setOverlaysWebView({ overlay: false }).catch(() => {})
  StatusBar.setBackgroundColor({ color: dark ? BAR_COLOR.dark : BAR_COLOR.light }).catch(() => {})
  StatusBar.setStyle({ style: dark ? Style.Dark : Style.Light }).catch(() => {})
}

export function setupNativeShell() {
  if (!Capacitor.isNativePlatform()) {
    return
  }

  const { theme } = useTheme()
  watch(theme, (value) => applyStatusBar(value === 'dark'), { immediate: true })

  // 安卓物理返回键：Tab 页退到后台（和微信一致），其他页面正常返回
  CapacitorApp.addListener('backButton', ({ canGoBack }) => {
    if (TAB_ROUTES.has(router.currentRoute.value.name)) {
      CapacitorApp.minimizeApp()
    } else if (canGoBack) {
      router.back()
    } else {
      CapacitorApp.minimizeApp()
    }
  })

  // 启动图是原生窗口背景，等首个路由（含懒加载的页面 chunk）就绪再撤，
  // 太早撤会露出还没渲染完的白底（capacitor.config.json 里 launchAutoHide 已关掉）
  router.isReady().then(() => setTimeout(() => SplashScreen.hide().catch(() => {}), 120))
}
