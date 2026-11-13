/*
 * 心语的 Service Worker。
 *
 * 只缓存「外壳」：HTML、打包后的 js/css、图标。
 * 接口（/api）、用户上传的图片（/uploads）、更新说明（/changelog.json）一律走网络，不进缓存 ——
 * 心情日记是最私人的东西，不能留在共享缓存里。
 */
const CACHE = 'xinyu-shell-v2'
const SHELL = ['/', '/manifest.webmanifest', '/icons/icon-192.png']

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting())
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  )
})

self.addEventListener('fetch', (event) => {
  const { request } = event
  if (request.method !== 'GET') return

  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return
  if (url.pathname.startsWith('/api') || url.pathname.startsWith('/uploads')) return
  // 更新说明要能「改完刷新就生效」，不能进缓存
  if (url.pathname.endsWith('/changelog.json')) return

  // 页面导航：先走网络，断网时回退到缓存的外壳
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request).catch(() => caches.match('/').then((res) => res || Response.error()))
    )
    return
  }

  event.respondWith(
    caches.match(request).then((cached) => {
      const network = fetch(request)
        .then((res) => {
          if (res.ok) {
            const copy = res.clone()
            caches.open(CACHE).then((cache) => cache.put(request, copy))
          }
          return res
        })
        .catch(() => cached)
      return cached || network
    })
  )
})
