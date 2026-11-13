<template>
  <div class="page">
    <van-nav-bar title="日记详情" left-arrow @click-left="router.back()" />

    <template v-if="diary">
      <div class="card xinyu-glass">
        <div class="meta">
          <van-tag v-if="pending" plain type="warning">待同步</van-tag>
          <van-tag v-if="diary.emotionLabel" type="primary">{{ diary.emotionLabel }}</van-tag>
          <span v-if="diary.emotionScore" class="score">{{ diary.emotionScore }} 分</span>
          <span class="time">{{ dateLabel(diary.createdAt) }}</span>
        </div>
        <p class="content">{{ diary.content }}</p>
        <div class="tags">
          <van-tag v-for="t in diary.emotionTags" :key="t" plain>{{ t }}</van-tag>
          <span v-if="diary.weather" class="info">{{ diary.weather }}</span>
          <span v-if="diary.scene" class="info">{{ diary.scene }}</span>
          <span class="info">{{ diary.privacy === 'PUBLIC' ? '公开' : '私密' }}</span>
        </div>
      </div>

      <div class="section-title">AI 的陪伴</div>
      <div v-if="streaming || diary.aiReply" class="card reply xinyu-glass">
        <p v-if="streaming && !replyText" class="loading">正在理解你的感受…</p>
        <p class="reply-text">{{ replyText || diary.aiReply }}</p>
      </div>
      <van-empty v-else :description="pending ? '联网同步后就能让 AI 陪你聊' : '让 AI 陪你说说话'" />

      <div class="submit">
        <van-button
          type="primary"
          round
          block
          :loading="streaming"
          :disabled="streaming || pending"
          @click="startAi"
        >
          {{ aiButtonText }}
        </van-button>
        <van-button round block plain type="danger" @click="onDelete">删除这篇日记</van-button>
      </div>
    </template>

    <van-empty v-else :description="loadFailed ? '离线中，这篇日记还没存到本机' : '日记不存在'" />

    <CrisisDialog v-model:show="showCrisis" />
  </div>
</template>

<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { deleteDiaryAny, getDiaryAny } from '../offline/diary'
import { isLocalId } from '../offline/outbox'
import { useOfflineStore } from '../stores/offline'
import { useUserStore } from '../stores/user'
import { getApiBase } from '../utils/backend'
import CrisisDialog from '../components/CrisisDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const offlineStore = useOfflineStore()
const diary = ref(null)
const replyText = ref('')
const streaming = ref(false)
const showCrisis = ref(false)
const loadFailed = ref(false)
// 本地 id 或者带 pending 标记：都说明这篇还没到服务器上，AI 回复自然也没法生成
const pending = computed(() => isLocalId(route.params.id) || !!diary.value?.pending)
const aiButtonText = computed(() => {
  if (pending.value) return '联网同步后可生成 AI 回复'
  return diary.value?.aiReply ? '重新生成 AI 回复' : '生成 AI 共情回复'
})
// SSE 连接由组件自己持有并负责关闭：EventSource 不会随组件卸载自动断开
let source = null

function closeSource() {
  if (source) {
    source.close()
    source = null
  }
}

function dateLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : ''
}

// 用 watch 而不是 onMounted：从一篇日记直接跳到另一篇时组件实例会被复用，
// onMounted 不会再执行，导致危机提示漏弹、AI 回复串到下一篇
watch(
  () => route.params.id,
  async (id) => {
    if (!id) return
    closeSource()
    diary.value = null
    replyText.value = ''
    showCrisis.value = false
    loadFailed.value = false
    try {
      // 本地 id 从待同步队列取；服务器 id 走接口，断网时 request.js 会拿缓存顶上
      const data = await getDiaryAny(id)
      if (!data) {
        loadFailed.value = true
        return
      }
      diary.value = data
      replyText.value = data.aiReply || ''
      // 翻到一篇写过「不想活」这类内容的日记时，同样给出关怀提示（保存时弹过也会再弹一次，
      // 这类内容值得多提醒一次；后端这里只是重跑关键词匹配，没有额外请求）
      if (data.crisisSupport) {
        showCrisis.value = true
      }
    } catch {
      loadFailed.value = true
    }
  },
  { immediate: true }
)

// 正在看这篇「待同步」日记时补传成功：换成服务器上的那条，AI 回复按钮也就解锁了
watch(
  () => offlineStore.syncedMap[route.params.id],
  (realId) => {
    if (realId) router.replace(`/diaries/${realId}`)
  },
  { immediate: true }
)

async function onDelete() {
  const local = isLocalId(route.params.id)
  try {
    await showConfirmDialog({
      title: '提示',
      message: local
        ? '这篇还没同步到服务器，删除后无法恢复。'
        : '删除后会在回收站里保留 7 天，期间可以恢复。'
    })
  } catch {
    return
  }
  await deleteDiaryAny(route.params.id)
  showToast(local ? '已删除' : '已放入回收站')
  offlineStore.refreshPending()
  router.replace('/diaries')
}

function startAi() {
  if (pending.value) {
    showToast('这篇日记还没同步，联网后再试')
    return
  }
  closeSource()
  const token = userStore.token
  const id = route.params.id
  source = new EventSource(
    `${getApiBase()}/diaries/${id}/ai-reply/stream?token=${encodeURIComponent(token)}`
  )

  streaming.value = true
  replyText.value = ''

  source.addEventListener('start', () => {})
  source.addEventListener('delta', (e) => {
    const data = JSON.parse(e.data)
    replyText.value += data.text
  })
  source.addEventListener('meta', (e) => {
    const data = JSON.parse(e.data)
    diary.value.emotionScore = data.emotionScore
    diary.value.emotionLabel = data.emotionLabel
  })
  source.addEventListener('crisis', (e) => {
    const data = JSON.parse(e.data)
    replyText.value = data.message
    // 关怀弹窗比一段文字更难被忽略，同时管理员那边也已经收到预警
    showCrisis.value = true
  })
  source.addEventListener('done', () => {
    diary.value.aiStatus = 'DONE'
    diary.value.aiReply = replyText.value
    streaming.value = false
    closeSource()
  })
  source.addEventListener('error', () => {
    streaming.value = false
    closeSource()
    showToast('AI 回复失败，请稍后再试')
  })
  source.onerror = () => {
    if (streaming.value) {
      streaming.value = false
      closeSource()
    }
  }
}

onUnmounted(() => {
  closeSource()
  streaming.value = false
})
</script>

<style scoped>
.card {
  border-radius: 16px;
  padding: 16px;
}
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.score {
  font-weight: 600;
  color: var(--xinyu-accent);
}
.time {
  margin-left: auto;
  color: var(--van-text-color-2);
  font-size: 12px;
}
.content {
  margin: 12px 0;
  line-height: 1.7;
  white-space: pre-wrap;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.info {
  color: var(--van-text-color-2);
  font-size: 12px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 20px 4px 8px;
}
.reply {
  min-height: 80px;
}
.reply-text {
  white-space: pre-wrap;
  line-height: 1.7;
}
.loading {
  color: var(--van-text-color-2);
}
.submit {
  margin: 20px 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
