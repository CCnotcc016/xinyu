<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div class="page">
    <van-nav-bar title="心语" />

    <CrisisAlertBanner />

    <div class="hello">
      <h2>你好，{{ userStore.user?.nickname || userStore.user?.username }}</h2>
      <p>今天感觉怎么样？</p>
    </div>

    <van-grid :column-num="3" :border="false">
      <van-grid-item icon="edit" text="写日记" @click="router.push('/diaries/write')" />
      <van-grid-item icon="chart-trending-o" text="情绪趋势" @click="router.push('/trend')" />
      <van-grid-item icon="friends-o" text="匿名树洞" @click="router.push('/treehole')" />
    </van-grid>

    <div class="section-title">最近日记</div>
    <van-skeleton v-if="loading" class="loading-card" title :row="1" />
    <van-empty v-else-if="diaries.length === 0" description="还没有日记，去写第一篇吧" />
    <div v-else class="xinyu-cards">
      <van-cell
        v-for="d in diaries"
        :key="d.id"
        :title="preview(d.content)"
        :label="dateLabel(d.createdAt)"
        is-link
        @click="router.push(`/diaries/${d.id}`)"
      >
        <template #value>
          <van-tag v-if="d.emotionLabel" type="primary">{{ d.emotionLabel }}</van-tag>
        </template>
      </van-cell>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { listDiaries } from '../api/diary'
import CrisisAlertBanner from '../components/CrisisAlertBanner.vue'

const router = useRouter()
const userStore = useUserStore()
const diaries = ref([])
// 有加载态才不会在数据回来之前先闪一下「还没有日记」
const loading = ref(true)

function preview(text) {
  return text.length > 30 ? text.slice(0, 30) + '…' : text
}
function dateLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : ''
}

onMounted(async () => {
  try {
    // PC 上卡片会排成两列，多取几条才填得满
    const data = await listDiaries({ page: 1, size: 6 })
    diaries.value = data.records ?? []
  } catch {
    // 失败就保持空列表，提示由 request.js 统一给出，这里不再叠一层
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.hello {
  padding: 8px 4px 16px;
}
.hello h2 {
  font-size: 22px;
}
.hello p {
  color: var(--van-text-color-2);
  margin-top: 4px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 20px 4px 8px;
}
.loading-card {
  padding: 14px;
  border-radius: 12px;
  background: var(--van-background-2);
}
</style>
