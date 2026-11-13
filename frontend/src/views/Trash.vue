<template>
  <div class="page">
    <van-nav-bar title="回收站" left-arrow @click-left="router.back()" />

    <p class="tip">删除的日记会在这里保留 7 天，期间随时可以恢复；超过 7 天会自动清除。</p>

    <van-loading v-if="loading" class="loading" />
    <van-empty v-else-if="!items.length" description="回收站是空的" />

    <div v-for="d in items" :key="d.id" class="item xinyu-glass">
      <div class="item__head">
        <span class="item__time">{{ timeLabel(d.createdAt) }}</span>
        <span class="item__left">还能恢复 {{ leftDays(d.deletedAt) }} 天</span>
      </div>
      <p class="item__content">{{ d.content }}</p>
      <div class="item__foot">
        <van-button size="small" type="primary" plain @click="onRestore(d)">恢复</van-button>
        <van-button size="small" plain @click="onPurge(d)">彻底删除</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { listTrash, purgeDiary, restoreDiary } from '../api/diary'

const router = useRouter()
const items = ref([])
const loading = ref(true)

const RETENTION_DAYS = 7

function timeLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : ''
}

function leftDays(dt) {
  if (!dt) return RETENTION_DAYS
  const deleted = new Date(dt.replace(' ', 'T')).getTime()
  const passed = Math.floor((Date.now() - deleted) / 86400000)
  return Math.max(RETENTION_DAYS - passed, 1)
}

async function load() {
  loading.value = true
  try {
    items.value = await listTrash()
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

async function onRestore(d) {
  try {
    await showConfirmDialog({ title: '恢复日记', message: '恢复后这篇日记会重新出现在列表里。' })
  } catch {
    return
  }
  await restoreDiary(d.id)
  items.value = items.value.filter((i) => i.id !== d.id)
  showToast('已恢复')
}

async function onPurge(d) {
  try {
    await showConfirmDialog({
      title: '彻底删除',
      message: '这篇日记和它的情绪标签会被永久删除，无法恢复。',
    })
  } catch {
    return
  }
  await purgeDiary(d.id)
  items.value = items.value.filter((i) => i.id !== d.id)
  showToast('已彻底删除')
}

onMounted(load)
</script>

<style scoped>
.tip {
  margin: 12px 4px 4px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--van-text-color-2);
}
.loading {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}
.item {
  margin-top: 12px;
  padding: 14px 16px;
  border-radius: 16px;
}
.item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11.5px;
  color: var(--van-text-color-3);
}
.item__left {
  color: var(--xinyu-accent);
}
.item__content {
  margin: 10px 0 0;
  font-size: 13.5px;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
.item__foot {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}
</style>
