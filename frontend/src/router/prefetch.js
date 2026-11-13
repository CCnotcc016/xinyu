/**
 * 离线写作要用到的页面，启动后趁有网先下载下来。
 *
 * 路由是按页分包（懒加载）的：第一次打开「写日记」时才去拉它那个 chunk。
 * 如果用户从没打开过这一页就断网了，点进去只会白屏 —— 而这恰恰是最需要用的时候。
 * 所以登录后空闲时把这几个页面预取一遍，浏览器和 Service Worker 都会把它存下来。
 *
 * 这些 import 和 router/index.js 里的是同一个模块路径，Rollup 会合并成同一个 chunk，
 * 不会因为这里多写一次就重复打包。
 */
const PREFETCH = [
  () => import('../views/DiaryWrite.vue'),
  () => import('../views/DiaryList.vue'),
  () => import('../views/DiaryDetail.vue'),
  () => import('../views/Home.vue')
]

let done = false

export function prefetchOfflinePages() {
  if (done) return
  done = true
  // 请求空闲时再拉，不和首屏抢带宽
  const run = () => PREFETCH.forEach((load) => load().catch(() => {}))
  if (typeof window.requestIdleCallback === 'function') {
    window.requestIdleCallback(run, { timeout: 4000 })
  } else {
    window.setTimeout(run, 2500)
  }
}
