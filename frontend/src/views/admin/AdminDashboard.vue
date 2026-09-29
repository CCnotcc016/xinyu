<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div class="page">
    <van-nav-bar title="后台管理" left-arrow @click-left="router.push('/profile')" />

    <van-tabs v-model:active="active" @change="onTab">
      <van-tab title="用户" name="admin-users" />
      <van-tab title="日记" name="admin-diaries" />
      <van-tab title="帖子" name="admin-posts" />
      <van-tab title="评论" name="admin-comments" />
      <van-tab title="举报" name="admin-reports" />
      <van-tab name="admin-crisis">
        <template #title>
          <span class="crisis-tab">
            预警
            <van-badge v-if="pendingCount > 0" :content="pendingCount" max="99" />
          </span>
        </template>
      </van-tab>
    </van-tabs>

    <router-view />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCrisisPendingCount } from '../../api/admin'

const route = useRoute()
const router = useRouter()
// van-tabs 会往 v-model 里写值，所以在 computed 上写入会失败（vue 会警告）。
// 这里用可写 ref，再跟随路由同步，浏览器前进/后退或直接输 url 也能对上。
const active = ref(route.name)
watch(
  () => route.name,
  (name) => {
    active.value = name
  }
)

const pendingCount = ref(0)
let timer = null

// 待处理预警数：进页面拉一次，之后轮询，管理员挂在后台时也能看到新求助
async function loadPendingCount() {
  try {
    const data = await getCrisisPendingCount()
    pendingCount.value = data?.count || 0
  } catch {
    // 计数失败不影响后台其它功能，静默忽略
  }
}

function onTab(name) {
  router.push({ name })
}

onMounted(() => {
  loadPendingCount()
  timer = setInterval(loadPendingCount, 60000)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.van-tabs {
  margin-bottom: 8px;
}
.crisis-tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
