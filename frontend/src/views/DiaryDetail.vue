<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div class="page">
    <van-nav-bar title="日记详情" left-arrow @click-left="router.back()" />

    <template v-if="diary">
      <div class="card">
        <div class="meta">
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
      <div v-if="streaming || diary.aiReply" class="card reply">
        <p v-if="streaming && !replyText" class="loading">正在理解你的感受…</p>
        <p class="reply-text">{{ replyText || diary.aiReply }}</p>
      </div>
      <van-empty v-else description="让 AI 陪你说说话" />

      <div class="submit">
        <van-button
          type="primary"
          round
          block
          :loading="streaming"
          :disabled="streaming"
          @click="startAi"
        >
          {{ diary.aiReply ? '重新生成 AI 回复' : '生成 AI 共情回复' }}
        </van-button>
        <van-button round block plain type="danger" @click="onDelete">删除这篇日记</van-button>
      </div>
    </template>

    <CrisisDialog v-model:show="showCrisis" />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getDiary, deleteDiary } from '../api/diary'
import { useUserStore } from '../stores/user'
import CrisisDialog from '../components/CrisisDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const diary = ref(null)
const replyText = ref('')
const streaming = ref(false)
const showCrisis = ref(false)
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

onMounted(async () => {
  diary.value = await getDiary(route.params.id)
  replyText.value = diary.value.aiReply || ''
  // 翻到一篇写过「不想活」这类内容的日记时，同样给出关怀提示（保存时弹过也会再弹一次，
  // 这类内容值得多提醒一次；后端这里只是重跑关键词匹配，没有额外请求）
  if (diary.value.crisisSupport) {
    showCrisis.value = true
  }
})

async function onDelete() {
  try {
    await showConfirmDialog({ title: '提示', message: '确定删除这篇日记吗？删除后无法恢复。' })
  } catch {
    return
  }
  await deleteDiary(route.params.id)
  showToast('已删除')
  router.replace('/diaries')
}

function startAi() {
  closeSource()
  const token = userStore.token
  const id = route.params.id
  source = new EventSource(`/api/diaries/${id}/ai-reply/stream?token=${encodeURIComponent(token)}`)

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
  background: var(--van-background-2);
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
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
