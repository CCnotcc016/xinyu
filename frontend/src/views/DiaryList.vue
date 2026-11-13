<template>
  <div class="page">
    <van-nav-bar title="我的日记" />
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        v-model:error="error"
        :finished="finished"
        :finished-text="offlineEmpty ? '' : '没有更多了'"
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
                <div class="tags">
                  <van-tag v-if="d.pending" plain type="warning">待同步</van-tag>
                  <van-tag v-if="d.emotionLabel" type="primary">{{ d.emotionLabel }}</van-tag>
                </div>
              </template>
            </van-cell>
            <template #right>
              <van-button square type="danger" text="删除" @click="onDelete(d.id)" />
            </template>
          </van-swipe-cell>
        </div>
      </van-list>
    </van-pull-refresh>
    <van-empty
      v-if="finished && diaries.length === 0"
      :description="offlineEmpty ? '离线中，这台设备还没缓存到日记' : '还没有日记'"
    />
    <div class="fab">
      <van-button type="primary" round icon="plus" @click="router.push('/diaries/write')">写日记</van-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { deleteDiaryAny, listDiariesMerged } from '../offline/diary'
import { isLocalId } from '../offline/outbox'
import { useUserStore } from '../stores/user'
import { useOfflineStore } from '../stores/offline'

const router = useRouter()
const userStore = useUserStore()
const offlineStore = useOfflineStore()
const diaries = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)
const refreshing = ref(false)
/** 断网又没有本地副本：空列表要说明原因，别写成「还没有日记」 */
const offlineEmpty = ref(false)

function preview(text) {
  return text.length > 40 ? text.slice(0, 40) + '…' : text
}
function dateLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : ''
}

async function onLoad() {
  try {
    const data = await listDiariesMerged({ page: page.value, size: 10 }, userStore.user?.id)
    offlineEmpty.value = Boolean(data.offline)
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
  refreshing.value = true
  try {
    // 下拉既是刷新也是「试试能不能同步」：联网时顺手把待同步的补上去
    if (offlineStore.online && offlineStore.pending > 0) await offlineStore.sync()
    const data = await listDiariesMerged({ page: 1, size: 10 }, userStore.user?.id)
    offlineEmpty.value = Boolean(data.offline)
    diaries.value = data.records
    finished.value = data.records.length < 10
    error.value = false
  } catch {
    error.value = true
  } finally {
    refreshing.value = false
  }
}

/** 补传成功：本地那条换成服务器上的，列表重新拉一次最省事 */
function onSynced() {
  if (diaries.value.some((d) => d.pending)) reload()
}

/** 网络回来了：之前空着是因为断网，重拉一次才看得到内容 */
function onOnline() {
  if (offlineEmpty.value || error.value) reload()
}

function reload() {
  page.value = 1
  diaries.value = []
  finished.value = false
  error.value = false
  offlineEmpty.value = false
  loading.value = true
  onLoad()
}

onMounted(() => {
  window.addEventListener('xinyu:synced', onSynced)
  window.addEventListener('online', onOnline)
})
onUnmounted(() => {
  window.removeEventListener('xinyu:synced', onSynced)
  window.removeEventListener('online', onOnline)
})

async function onDelete(id) {
  const local = isLocalId(id)
  await showConfirmDialog({
    title: '提示',
    message: local ? '这篇还没同步到服务器，删除后无法恢复。' : '删除后会在回收站里保留 7 天，期间可以恢复。'
  })
  await deleteDiaryAny(id)
  showToast(local ? '已删除' : '已放入回收站')
  diaries.value = diaries.value.filter((d) => d.id !== id)
  offlineStore.refreshPending()
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

.tags {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
</style>
