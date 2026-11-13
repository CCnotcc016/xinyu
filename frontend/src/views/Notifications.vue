<template>
  <div class="page">
    <van-nav-bar title="消息通知" left-arrow @click-left="router.back()">
      <template #right>
        <span v-if="chatStore.notifications > 0" class="read-all" @click="readAll">全部已读</span>
      </template>
    </van-nav-bar>

    <van-list
      v-model:loading="loading"
      v-model:error="error"
      :finished="finished"
      finished-text="没有更多了"
      error-text="加载失败，点击重新加载"
      @load="onLoad"
    >
      <div v-if="finished && !items.length" class="empty">还没有消息，去树洞里逛逛吧</div>
      <div
        v-for="n in items"
        :key="n.id"
        class="notice xinyu-glass"
        :class="{ 'notice--unread': !n.read }"
        @click="open(n)"
      >
        <div class="notice__head">
          <span class="notice__title">
            <i v-if="!n.read" class="dot" />
            {{ n.title }}
          </span>
          <span class="notice__time">{{ timeLabel(n.createdAt) }}</span>
        </div>
        <p v-if="n.content" class="notice__content">{{ n.content }}</p>
      </div>
    </van-list>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { listNotifications, readNotification, readAllNotifications } from '../api/notification'
import { useChatStore } from '../stores/chat'

const router = useRouter()
const chatStore = useChatStore()
const items = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)

function timeLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(5, 16) : ''
}

async function onLoad() {
  try {
    const data = await listNotifications({ page: page.value, size: 20 })
    items.value.push(...data.records)
    if (data.records.length < 20 || items.value.length >= data.total) {
      finished.value = true
    } else {
      page.value += 1
    }
  } catch {
    error.value = true
  } finally {
    // 失败时必须复位，否则 van-list 会一直卡在 loading 状态
    loading.value = false
  }
}

/** 通知点进去要跳到对应页面：帖子 / 用户主页 / 私聊 / 举报结果 */
function target(n) {
  if (n.type === 'REPORT') return '/reports'
  if (n.targetType === 'POST' && n.targetId) return `/treehole/${n.targetId}`
  if (n.targetType === 'USER' && n.targetId) return `/u/${n.targetId}`
  if (n.targetType === 'CONVERSATION' && n.targetId) return `/chat/${n.targetId}`
  return null
}

async function open(n) {
  if (!n.read) {
    n.read = true
    chatStore.notifications = Math.max(0, chatStore.notifications - 1)
    try {
      await readNotification(n.id)
    } catch {
      // 标记已读失败不影响跳转，下次进来还会是未读
    }
  }
  const to = target(n)
  if (to) router.push(to)
}

async function readAll() {
  try {
    await readAllNotifications()
    items.value.forEach((n) => (n.read = true))
    chatStore.notifications = 0
    showToast('已全部标记为已读')
  } catch {
    // 失败提示由 request.js 统一给出
  }
}

onMounted(() => chatStore.refresh())
</script>

<style scoped>
.read-all {
  color: var(--xinyu-accent);
  font-size: 14px;
}
.empty {
  padding: 60px 0;
  text-align: center;
  color: var(--van-text-color-2);
  font-size: 14px;
}
.notice {
  border-radius: 16px;
  padding: 14px 16px;
  margin: 8px 0;
}
.notice--unread {
  background: var(--xinyu-accent-soft, rgba(238, 10, 36, 0.06));
}
.notice__head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 10px;
}
.notice__title {
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ee0a24;
  display: inline-block;
}
.notice__time {
  color: var(--van-text-color-2);
  font-size: 12px;
  white-space: nowrap;
}
.notice__content {
  margin: 6px 0 0;
  color: var(--van-text-color-2);
  font-size: 14px;
  line-height: 1.5;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
@media (min-width: 1024px) {
  .notice {
    margin: 10px 0;
  }
}
</style>
