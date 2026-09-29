<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div class="page">
    <van-nav-bar title="我的日记" />
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        v-model:error="error"
        :finished="finished"
        finished-text="没有更多了"
        error-text="加载失败，点击重新加载"
        @load="onLoad"
      >
        <div class="xinyu-cards">
          <van-swipe-cell v-for="d in diaries" :key="d.id">
            <van-cell
              :title="preview(d.content)"
              :label="`${dateLabel(d.createdAt)}${d.emotionScore ? ' · ' + d.emotionScore + '分' : ''}`"
              is-link
              @click="router.push(`/diaries/${d.id}`)"
            >
              <template #value>
                <van-tag v-if="d.emotionLabel" type="primary">{{ d.emotionLabel }}</van-tag>
              </template>
            </van-cell>
            <template #right>
              <van-button square type="danger" text="删除" @click="onDelete(d.id)" />
            </template>
          </van-swipe-cell>
        </div>
      </van-list>
    </van-pull-refresh>
    <van-empty v-if="finished && diaries.length === 0" description="还没有日记" />
    <div class="fab">
      <van-button type="primary" round icon="plus" @click="router.push('/diaries/write')">写日记</van-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { listDiaries, deleteDiary } from '../api/diary'

const router = useRouter()
const diaries = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)
const refreshing = ref(false)

function preview(text) {
  return text.length > 40 ? text.slice(0, 40) + '…' : text
}
function dateLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : ''
}

async function onLoad() {
  try {
    const data = await listDiaries({ page: page.value, size: 10 })
    diaries.value.push(...data.records)
    if (data.records.length < 10 || diaries.value.length >= data.total) {
      finished.value = true
    } else {
      page.value += 1
    }
  } catch {
    // 进入 error 态：van-list 显示「点击重新加载」并暂停自动加载，避免失败时无限刷接口
    error.value = true
  } finally {
    // 必须在 finally 里复位：请求失败时若停在 true，van-list 会永久卡死无法重试
    loading.value = false
  }
}

async function onRefresh() {
  page.value = 1
  const data = await listDiaries({ page: 1, size: 10 })
  diaries.value = data.records
  finished.value = data.records.length < 10
  error.value = false
  refreshing.value = false
}

async function onDelete(id) {
  await showConfirmDialog({ title: '提示', message: '确定删除这篇日记吗？' })
  await deleteDiary(id)
  showToast('已删除')
  diaries.value = diaries.value.filter((d) => d.id !== id)
}
</script>

<style scoped>
.fab {
  position: fixed;
  /* 宽屏时外壳居中，按钮要贴着外壳内侧，不能贴着浏览器右边 */
  right: max(20px, calc((100vw - var(--xinyu-shell)) / 2 + 20px));
  bottom: 80px;
  z-index: 9;
}
</style>
