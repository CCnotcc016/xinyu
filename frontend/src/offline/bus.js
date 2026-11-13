/**
 * 网络状态事件总线。
 *
 * request.js 里能直接判断「这次请求是不是没连上」，但它不方便直接引用 Pinia 的 store
 * （会形成 request → store → api → request 的循环）。所以这里只做一个极薄的事件通道：
 * request.js 往里投递，stores/offline.js 订阅后更新界面状态。
 */
const target = new EventTarget()

export const NET_OFFLINE = 'xinyu-net-offline'
export const NET_ONLINE = 'xinyu-net-online'

/** 请求拿到了响应（哪怕是 4xx / 5xx），说明接口是通的 */
export function reportReachable() {
  target.dispatchEvent(new Event(NET_ONLINE))
}

/** 请求连响应都没拿到：断网、DNS 失败、连接被拒或超时 */
export function reportUnreachable() {
  target.dispatchEvent(new Event(NET_OFFLINE))
}

export function onNetworkChange(handler) {
  const onOnline = () => handler(true)
  const onOffline = () => handler(false)
  target.addEventListener(NET_ONLINE, onOnline)
  target.addEventListener(NET_OFFLINE, onOffline)
  return () => {
    target.removeEventListener(NET_ONLINE, onOnline)
    target.removeEventListener(NET_OFFLINE, onOffline)
  }
}
