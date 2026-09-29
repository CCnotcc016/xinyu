<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <van-config-provider :theme="theme">
    <router-view v-slot="{ Component }">
      <transition name="page" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
    <!-- 底部导航放在路由之外：切页时它不会跟着一起淡出，滑动指示器也就不会闪 -->
    <TabBar v-if="route.meta.tab" />
    <UpdateNotice />
    <BindPhoneNotice />
  </van-config-provider>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import UpdateNotice from './components/UpdateNotice.vue'
import BindPhoneNotice from './components/BindPhoneNotice.vue'
import TabBar from './components/TabBar.vue'
import { useUpdateNotice } from './composables/useUpdateNotice'
import { useTheme } from './composables/useTheme'
import { useUserStore } from './stores/user'
import { getMe } from './api/user'

const route = useRoute()
const { openIfUpdated } = useUpdateNotice()
// 浅色 / 深色 / 跟随系统由用户自己选，见 composables/useTheme.js
const { theme } = useTheme()
const userStore = useUserStore()

// 版本号变了才弹，用户关掉后到下个版本之前都不会再出现
onMounted(openIfUpdated)

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
