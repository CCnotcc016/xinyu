<template>
  <van-config-provider :theme="theme">
    <router-view v-slot="{ Component }">
      <transition name="page" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
    <!-- 底部导航放在路由之外：切页时它不会跟着一起淡出，滑动指示器也就不会闪 -->
    <TabBar v-if="route.meta.tab" />
    <OfflineBanner />
    <UpdateNotice />
    <BindPhoneNotice />
    <!-- 放最后：van-popup 的层级按挂载顺序递增，协议弹窗要压在其他弹层上面 -->
    <AgreementGate />
  </van-config-provider>
</template>

<script setup>
import { onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import UpdateNotice from './components/UpdateNotice.vue'
import BindPhoneNotice from './components/BindPhoneNotice.vue'
import AgreementGate from './components/AgreementGate.vue'
import TabBar from './components/TabBar.vue'
import OfflineBanner from './components/OfflineBanner.vue'
import { useUpdateNotice } from './composables/useUpdateNotice'
import { startReminderScheduler, stopReminderScheduler } from './composables/useReminder'
import { useTheme } from './composables/useTheme'
import { useUserStore } from './stores/user'
import { useChatStore } from './stores/chat'
import { useOfflineStore } from './stores/offline'
import { prefetchOfflinePages } from './router/prefetch'
import { getMe } from './api/user'

const route = useRoute()
const router = useRouter()
const { openIfUpdated } = useUpdateNotice()
// 浅色 / 深色 / 跟随系统由用户自己选，见 composables/useTheme.js
const { theme } = useTheme()
const userStore = useUserStore()
const chatStore = useChatStore()
const offlineStore = useOfflineStore()

// 离线队列要在登录后才归属到具体账号，用户信息拉到之后再开始补传最稳妥
watch(
  () => userStore.user?.id,
  (id) => {
    if (id) {
      offlineStore.init()
      // 登录后趁有网把写日记等页面预取下来，断网时也进得去
      prefetchOfflinePages()
    } else {
      offlineStore.reset()
    }
  },
  { immediate: true }
)
onUnmounted(() => offlineStore.dispose())

// 未读角标靠轮询维护：登录后开始，退出登录立刻停掉
watch(
  () => userStore.token,
  (token) => (token ? chatStore.startPolling() : chatStore.stopPolling()),
  { immediate: true }
)
onUnmounted(() => chatStore.stopPolling())

// 写日记提醒：到点且今天还没提醒过，就弹一次；网页关掉后不会再叫，属于纯前端提醒
watch(
  () => userStore.token,
  () => {
    if (userStore.token && !userStore.isStaff) {
      startReminderScheduler({
        shouldRun: () => !!userStore.token && !userStore.isStaff,
        onWrite: () => router.push('/diaries/write')
      })
    } else {
      stopReminderScheduler()
    }
  },
  { immediate: true }
)
onUnmounted(stopReminderScheduler)

// 版本号变了才弹，用户关掉后到下个版本之前都不会再出现
// 客服账号只进工作台，不需要发版弹窗打扰
onMounted(() => {
  if (!userStore.isStaff) openIfUpdated()
})

// 本地缓存的用户信息可能是旧版本存的（例如没有 phoneBound 字段），
// 启动时拉一次最新的，「补绑手机号」提醒之类的判断才不会失效。
onMounted(async () => {
  if (!userStore.token) return
  try {
    userStore.setUser(await getMe())
  } catch {
    // 401 已由 request.js 统一处理（清 token + 跳登录），这里不用再做什么
  }
})
</script>
